package schema

/**
 * One column of a caller's requested `ORDER BY`, passed to `Table.selectByRange`.
 *
 * @property descending The direction the *caller* wants — not to be confused with
 *   [IndexColumn.descending], the index's own declared direction for that column. Whether the two
 *   agree is exactly what `Table.resolveScanDirection` checks to pick a plain FORWARD/BACKWARD
 *   tree walk.
 * */
data class ColumnOrder(
    val name: String,
    val descending: Boolean
)
