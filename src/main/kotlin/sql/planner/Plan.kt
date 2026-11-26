package sql.planner

import schema.Bound
import schema.ColumnOrder
import sql.expr.Predicate

/**
 * How to run one statement against one table, as a small tree: a scan at the bottom, then whatever is stacked on top.
 * Built by [Planner]; the shape is always `Limit( Sort( Filter( IndexScan | FullScan ) ) )` with each upper layer
 * optional.
 */
sealed interface Plan

/**
 * `Table.selectByRange(indexName, lower, upper, orderBy)`. [lower] and [upper] are in the index's byte order (see
 * [IndexMatch.lower]); [orderBy] holds only the free key columns, never one pinned by `=`.
 */
data class IndexScan(
    val indexName: String,
    val lower: Bound<List<Any?>>,
    val upper: Bound<List<Any?>>,
    val orderBy: List<ColumnOrder>
) : Plan

/** Every row of the table. [Planner] always leaves [orderBy] empty: an order it needs comes from a [Sort] on top. */
data class FullScan(val orderBy: List<ColumnOrder>) : Plan

/** The rows of [child] for which [predicate] is TRUE (FALSE and UNKNOWN are dropped). */
data class Filter(val child: Plan, val predicate: Predicate) : Plan

/** The rows of [child] in [orderBy] order. NULL sorts first in an ascending column and last in a descending one. */
data class Sort(val child: Plan, val orderBy: List<ColumnOrder>) : Plan

/** Skips [offset] rows of [child], then keeps at most [limit]; null means no skip / no cap. */
data class Limit(val child: Plan, val limit: Int?, val offset: Int?) : Plan
