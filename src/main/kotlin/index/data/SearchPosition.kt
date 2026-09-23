package index.data

/**
 * Where an [index.btree.Cursor] currently stands: a page plus a slot on it.
 *
 * @property pageId The leaf page this position is on.
 * @property idx The slot index, or null meaning "resolve lazily" — [index.btree.Cursor.step]
 *   treats a null [idx] as slot 0 for [index.btree.ScanDirection.FORWARD] or the last slot for
 *   [index.btree.ScanDirection.BACKWARD], letting a position be created before [pageId]'s
 *   `recordCount` is known (e.g. right after landing on a brand-new page mid-walk). The one
 *   exception is `SearchPosition(`[util.INVALID_PAGE_ID]`, null)`, which instead means the walk
 *   itself is over — [pageId] is checked for that sentinel before [idx] is ever consulted.
 * */
data class SearchPosition(
    val pageId: Long,
    val idx: Int?
)
