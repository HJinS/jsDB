package sql.planner

import schema.Bound
import schema.ColumnOrder
import schema.IndexColumn
import schema.IndexRow
import schema.RowSchema
import sql.expr.CompareOp
import sql.expr.Predicate

/**
 * What one index can do for a WHERE + ORDER BY.
 *
 * @property equalityCount How many leading key columns are pinned to a single value by `=`.
 * @property rangeColumn The one key column right after the pinned ones that narrows the scan with `<`, `<=`, `>`,
 *   `>=`, or null if none does.
 * @property lower The `lowerBound` to hand to `Table.selectByRange`. **Lower/upper follow the index's byte order, not
 *   value order**: on a DESC [rangeColumn] the larger values come first, so `b > 5` ends up in [upper].
 * @property upper See [lower].
 * @property consumed The WHERE terms the bounds fully express (only the `=` terms). Range terms are deliberately not
 *   here: NULL sorts first in an ASC column and last in a DESC one, so a one-sided range scan also walks over NULL
 *   rows, which only the row-by-row filter (`NULL < 5` is UNKNOWN) drops. Everything not consumed is the residual
 *   filter.
 * @property satisfiesOrderBy Whether walking this index already produces the requested ORDER BY, with no Sort needed.
 * @property scanOrderBy The `orderBy` to pass to `Table.selectByRange` for this index: the requested ORDER BY minus
 *   columns pinned by `=` (the table matches ORDER BY against the *free* key columns only, and throws on a pinned
 *   one), or empty when [satisfiesOrderBy] is false (the plan sorts afterwards instead).
 */
data class IndexMatch(
    val index: IndexRow,
    val equalityCount: Int,
    val rangeColumn: String?,
    val lower: Bound<List<Any?>>,
    val upper: Bound<List<Any?>>,
    val consumed: Set<Predicate>,
    val satisfiesOrderBy: Boolean,
    val scanOrderBy: List<ColumnOrder>,
)

/** One end of a range on a single column, already coerced to the column's key type. */
private data class Edge(val value: Any, val inclusive: Boolean)

/**
 * Works out how much of [conditions] and [orderBy] [index] can serve, or null if it can serve none of it (then using
 * it would just be a full scan through a secondary index).
 *
 * 1. **Equality prefix**: walk the key columns from the first; each one with a usable `=` condition is pinned, and the
 *    walk stops at the first one without.
 * 2. **One range column**: the key column right after the pinned ones may carry a lower and/or an upper limit. A
 *    composite index is a single byte interval, so only this one column can be narrowed.
 * 3. **Bounds**: pinned values are copied into *both* bounds (see `Table.selectByRange`'s caller contract); the range
 *    limits are appended to the side they belong to.
 * 4. **ORDER BY**: see [satisfiesOrder].
 *
 * Anything that doesn't make it into the bounds - `<>`, a condition on a column past a gap, a second limit on the
 * same side, a literal [coerceLiteral] can't represent exactly - is left to the residual filter, so skipping a
 * condition here is always safe.
 *
 * Condition columns are [sql.expr.ColumnExpr]s (a position in [schema]) while index key columns are named, so the two
 * are joined through [RowSchema.columnIndex].
 */
fun matchIndex(
    schema: RowSchema,
    index: IndexRow,
    conditions: List<ColumnCondition>,
    orderBy: List<ColumnOrder>
): IndexMatch? {

    val byPosition: Map<Int, List<ColumnCondition>> = conditions.groupBy { it.column.index }

    fun conditionsOn(column: IndexColumn): List<ColumnCondition> =
        byPosition[schema.columnIndex(column.name)].orEmpty()

    // 1) equality prefix
    val equalityValues = mutableListOf<Any?>()
    val consumed = mutableSetOf<Predicate>()
    for (column in index.keyColumns) {
        val (condition, value) = conditionsOn(column).firstNotNullOfOrNull { candidate ->
            if (candidate.op != CompareOp.EQ) null
            else coerceLiteral(candidate.value, column)?.let { candidate to it }
        } ?: break
        equalityValues += value
        consumed += condition.source
    }

    // 2) range column: the next key column, with at most one limit per side
    val rangeColumn = index.keyColumns.getOrNull(equalityValues.size)
    var valueLower: Edge? = null   // `col > x` / `col >= x`
    var valueUpper: Edge? = null   // `col < x` / `col <= x`
    if (rangeColumn != null) {
        for ((_, op, value) in conditionsOn(rangeColumn)) {
            val inclusive = op == CompareOp.GE || op == CompareOp.LE
            when (op) {
                CompareOp.GT, CompareOp.GE ->
                    if (valueLower == null) valueLower =
                        coerceLiteral(value, rangeColumn)?.let { Edge(it, inclusive) }

                CompareOp.LT, CompareOp.LE ->
                    if (valueUpper == null) valueUpper =
                        coerceLiteral(value, rangeColumn)?.let { Edge(it, inclusive) }

                else -> {}
            }
        }
        // Both ends on the same value would make Table count the range column as pinned and then reject an
        // ORDER BY that names it. Such a range is rare, so just leave it to the filter.
        if (valueLower != null && valueUpper != null && valueLower.value == valueUpper.value) {
            valueLower = null
            valueUpper = null
        }
    }
    val hasRange = valueLower != null || valueUpper != null

    // 3) bounds, in the index's byte order: a DESC column stores larger values first, so its limits swap sides
    val (byteLower, byteUpper) = if (rangeColumn?.descending == true) valueUpper to valueLower else valueLower to valueUpper

    fun bound(edge: Edge?): Bound<List<Any?>> = when {
        edge != null -> Bound(equalityValues + edge.value, edge.inclusive)
        equalityValues.isNotEmpty() -> Bound(equalityValues.toList(), true)
        else -> Bound(null, true)
    }

    // 4) ORDER BY, minus the columns this scan pins to one value
    val pinned = index.keyColumns.take(equalityValues.size).map { it.name }.toSet()
    val effectiveOrder = orderBy.filterNot { it.name in pinned }
    val satisfies = satisfiesOrder(index.keyColumns, effectiveOrder, equalityValues.size)

    val usable = equalityValues.isNotEmpty() || hasRange || (effectiveOrder.isNotEmpty() && satisfies)
    if (!usable) return null

    return IndexMatch(
        index = index,
        equalityCount = equalityValues.size,
        rangeColumn = if (hasRange) rangeColumn?.name else null,
        lower = bound(byteLower),
        upper = bound(byteUpper),
        consumed = consumed,
        satisfiesOrderBy = satisfies,
        scanOrderBy = if (satisfies) effectiveOrder else emptyList(),
    )
}

/**
 * Picks the index to scan from [matches] (every index [matchIndex] found usable), or null if [matches] is empty
 * (the planner then falls back to a full scan).
 *
 * Compare two candidates by these keys, in order; the first key that differs decides:
 * 1. more [IndexMatch.equalityCount] wins - each pinned column narrows the scan the most;
 * 2. having a [IndexMatch.rangeColumn] wins over not having one;
 * 3. [IndexMatch.satisfiesOrderBy] wins - no Sort needed afterwards;
 * 4. a primary or unique index wins over a plain one (primary and unique count the same).
 *
 * If two candidates are equal on all four keys, the one that comes **first** in [matches] wins.
 */
fun chooseBest(matches: List<IndexMatch>): IndexMatch? =
    matches.maxWithOrNull(
        compareBy<IndexMatch> { it.equalityCount }
            .thenBy { it.rangeColumn != null }
            .thenBy { it.satisfiesOrderBy }
            .thenBy { it.index.isPrimary || it.index.isUnique }
    )

/**
 * Whether walking an index whose first [equalityCount] key columns are pinned produces [orderBy] without a Sort. This
 * mirrors `Table.resolveScanDirection` (which throws instead of answering), so a plan never hands the table an ORDER
 * BY it would reject:
 * - [orderBy] must name the free key columns (those after the pinned ones) in key order, as an unbroken prefix;
 * - and then either every named column has the direction the index declares, or every one has the opposite (the walk
 *   goes backward). A mix of the two can't be produced by one walk.
 *
 * [orderBy] must already be free of pinned columns. An empty [orderBy] is always satisfied.
 */
fun satisfiesOrder(keyColumns: List<IndexColumn>, orderBy: List<ColumnOrder>, equalityCount: Int): Boolean {
    if (orderBy.isEmpty()) return true
    val free = keyColumns.drop(equalityCount)
    if (orderBy.size > free.size) return false

    val paired = free.zip(orderBy)
    return paired.all { (column, order) -> column.name == order.name }
            && (paired.all { (column, order) -> column.descending == order.descending }
            || paired.all { (column, order) -> column.descending != order.descending })
}
