package sql.planner

import exception.DatabaseException
import schema.ColumnOrder
import schema.IndexRow
import schema.RowSchema
import sql.ast.Select
import sql.expr.Predicate
import util.EntityType
import util.ErrorCode
import util.SQLErrorDetail
import util.requireOrThrow

/**
 * Turns a bound statement into a [Plan] for one table: [schema] is its column layout and [indexes] every index on it
 * (primary included; see `Table.indexes`).
 */
class Planner(
    private val schema: RowSchema,
    private val indexes: List<IndexRow>,
) {
    /**
     * Plans a whole SELECT. [where] is [select]'s WHERE already bound (the binder runs before the planner), so
     * `select.where` itself is not looked at. The column list isn't either: projection is the executor's job.
     *
     * The result is [planScan]'s plan with an optional Limit on the very outside:
     * ```
     * Limit( Sort( Filter( IndexScan | FullScan ) ) )
     * ```
     * 1. **ORDER BY**: each `OrderItem(column, descending)` becomes `ColumnOrder(column, descending)`, keeping the
     *    order. Every column must exist in [schema]; an unknown one throws [exception.DatabaseException]
     *    ([util.ErrorCode.UNDEFINED_COLUMN]) here, before anything is planned. ([RowSchema.columnIndex] already
     *    throws exactly that.)
     * 2. **Scan**: `planScan(where, that ORDER BY)`.
     * 3. **Limit**: only when `select.limit` or `select.offset` is set; the other one stays null. `LIMIT 0` and
     *    `OFFSET 0` still count as set.
     *    - negative → [exception.DatabaseException] with [util.ErrorCode.NEGATIVE_LIMIT] /
     *      [util.ErrorCode.NEGATIVE_OFFSET] (the parser accepts `LIMIT -1`).
     *    - the parser gives `Long`, the table takes `Int`: anything above [Int.MAX_VALUE] becomes [Int.MAX_VALUE]. No
     *      table can hold more rows than that, so the result is the same.
     *    - Limit goes outside the Sort: rows are cut *after* they are in order.
     */
    fun plan(select: Select, where: Predicate?): Plan {
        val orderBy = select.orderBy.map { item ->
            schema.columnIndex(item.column) // only to reject an unknown column
            ColumnOrder(item.column, item.descending)
        }
        val limit = rowCount(select.limit, "LIMIT", ErrorCode.NEGATIVE_LIMIT, select.table)
        val offset = rowCount(select.offset, "OFFSET", ErrorCode.NEGATIVE_OFFSET, select.table)

        val scan = planScan(where, orderBy)
        return if (limit == null && offset == null) scan else Limit(scan, limit, offset)
    }

    /**
     * A LIMIT/OFFSET [value] as the `Int` the table takes: null stays null, above [Int.MAX_VALUE] becomes
     * [Int.MAX_VALUE].
     *
     * @throws DatabaseException ([negativeCode]) if [value] is negative.
     */
    private fun rowCount(value: Long?, keyword: String, negativeCode: ErrorCode, table: String): Int? {
        if (value == null) return null
        requireOrThrow(value >= 0) {
            DatabaseException(
                negativeCode,
                SQLErrorDetail(
                    reason = "$keyword must not be negative (got $value)",
                    entityType = EntityType.TABLE,
                    entityName = table,
                )
            )
        }
        return value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Turns one WHERE ([where], already bound) and the requested [orderBy] into a scan plan. SELECT, UPDATE and DELETE
     * all share it; `plan` only adds LIMIT/OFFSET on top.
     *
     * The result is always this shape, innermost first (Filter and Sort only when needed):
     * ```
     * Sort( Filter( IndexScan | FullScan ) )
     * ```
     * 1. **Scan**: run [matchIndex] on every index in [indexes] and pick one with [chooseBest].
     *    - picked → `IndexScan(its name, its lower, its upper, its scanOrderBy)`
     *    - none → `FullScan(emptyList())`. If the primary index could have produced the order it would have been a
     *      candidate itself, so a full scan never promises an order.
     * 2. **Filter**: every AND term of [where] that the picked index did *not* consume, in their original order,
     *    joined left to right: `t1`, `And(t1, t2)`, `And(And(t1, t2), t3)`, ... No terms left → no Filter.
     * 3. **Sort**: added with the original [orderBy] when [orderBy] isn't empty and the scan doesn't already produce it
     *    (nothing was picked, or the pick has `satisfiesOrderBy = false`). Sort wraps the Filter, so only the rows
     *    that survive it get sorted.
     */
    fun planScan(where: Predicate?, orderBy: List<ColumnOrder>): Plan {
        val terms = where?.let(::conjuncts).orEmpty()
        val conditions = terms.mapNotNull { it.asColumnCondition() }

        // 1) Scan
        val best = chooseBest(indexes.mapNotNull { matchIndex(schema, it, conditions, orderBy) })
        var plan: Plan =
            best?.let { IndexScan(it.index.indexName, it.lower, it.upper, it.scanOrderBy) } ?: FullScan(emptyList())

        // 2) Filter: what the bounds don't already express
        val residual = andAll(terms.filterNot { best != null && it in best.consumed })
        if (residual != null) plan = Filter(plan, residual)

        // 3) Sort: only when the scan doesn't already give the order
        if (orderBy.isNotEmpty() && best?.satisfiesOrderBy != true) plan = Sort(plan, orderBy)

        return plan
    }

}
