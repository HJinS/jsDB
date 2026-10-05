package database

import exception.TableException
import index.btree.ScanDirection
import index.serializer.KeySerializer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Arrays
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock
import schema.Bound
import schema.ColumnOrder
import schema.IndexColumn
import schema.IndexHandle
import schema.Row
import schema.RowSchema
import util.EntityType
import util.SQLErrorDetail
import util.requireOrThrow

private val logger = KotlinLogging.logger {}

/**
 * Row-level CRUD for one table, built on top of its indexes' byte-only [index.btree.BTree]s. This
 * is the boundary where domain values meet bytes: [IndexHandle]'s serializers are only ever called
 * from here (or from [catalog.CatalogManager] for its own, unrelated catalog schemas) — the BTree
 * layer itself never sees a typed key or value.
 *
 * Index-organized: the primary index's leaves store the full row
 * ([IndexHandle.valueSerializer]-encoded); every secondary index instead stores the primary key, so
 * a secondary hit needs one extra lookup through the primary index (see [extractData]).
 *
 * @property rowSchema The table's column layout, shared by every row this instance produces.
 * @property primaryIndex The clustered index rows are actually stored under.
 * @property secondaryIndexes Every other index on this table, keyed by index name.
 */
class Table(
    private val rowSchema: RowSchema,
    private val primaryIndex: IndexHandle,
    private val secondaryIndexes: Map<String, IndexHandle>,
    private val readLock: Lock,
) {

    /**
     * Inserts [row] into the primary index and every secondary index.
     *
     * Uniqueness is checked for the primary key and for every secondary index declared unique
     * *before* anything is written, so a violation never leaves a partial insert behind. Each
     * secondary index stores its value using [IndexHandle.valueSerializer] (not
     * [IndexHandle.keySerializer]) — that's the same encoding [extractData] expects when it later
     * resolves a secondary hit back to a row, and the encoding [updateRow] must keep using for the
     * same purpose (see the note there for why this mattered).
     */
    fun insertRow(row: Row) {
        readLock.withLock {
            val primaryTree = primaryIndex.btree
            val primaryKey = primaryIndex.extractKey(row)
            val primarySerialized = primaryIndex.keySerializer.serialize(primaryKey)
            if (primaryTree.search(primarySerialized) != null)
                throw TableException.UniqueViolation(
                    SQLErrorDetail(
                        entityType = EntityType.PRIMARY_INDEX,
                        entityName = primaryIndex.metadata.indexName,
                        tableName = primaryIndex.metadata.tableName,
                        columnNames = primaryIndex.columnNames,
                    )
                )

            for ((_, handle) in secondaryIndexes) {
                val indexKey = handle.extractKey(row)
                val indexSerialized = handle.keySerializer.serialize(indexKey)
                if (handle.metadata.isUnique && handle.btree.search(indexSerialized) != null) {
                    throw TableException.UniqueViolation(
                        SQLErrorDetail(
                            entityType = EntityType.INDEX,
                            entityName = handle.metadata.indexName,
                            tableName = handle.metadata.tableName,
                            columnNames = handle.columnNames,
                        )
                    )
                }
            }
            val rowSerialized = primaryIndex.valueSerializer.serialize(row.toList())
            primaryTree.insert(primarySerialized, rowSerialized)

            for ((_, handle) in secondaryIndexes) {
                val indexKey = handle.extractKey(row)
                val indexSerialized = handle.keySerializer.serialize(indexKey)
                val indexValueSerialized = handle.valueSerializer.serialize(primaryKey)
                handle.btree.insert(indexSerialized, indexValueSerialized)
            }
        }
    }

    /** Point lookup by primary key. Returns null if no row is stored under [key]. */
    fun selectByKey(key: List<Any?>): Row? {
        readLock.withLock {
            val primaryTree = primaryIndex.btree
            val keySerialized = primaryIndex.keySerializer.serialize(key)
            val searchResult = primaryTree.search(keySerialized)
            return searchResult?.let { extractData(primaryIndex, it) }
        }
    }

    /**
     * Runs a range scan over [indexName] between [lowerBound] and [upperBound], in the order
     * requested by [orderBy], and returns every matching row eagerly as a [List] (never a lazy
     * [index.btree.Cursor] — see the locking note below for why).
     *
     * ### This is also how prefix search works
     * There is no separate mechanism for a pure prefix search (e.g. `col1 = 5`) — it's just the
     * degenerate case where [lowerBound] and [upperBound] carry the same value with `isInclusive =
     * true` on both sides. [index.btree.BTree] never sees the difference between "prefix search"
     * and "explicit range"; only the boundary bytes this function builds differ, and both go
     * through the exact same seek/step/stop mechanism.
     *
     * ### Caller contract for [lowerBound] / [upperBound]
     * Any leading column pinned by equality (e.g. `col1 = 5` in `col1 = 5 AND col2 > 10`) MUST be
     * mirrored into *both* bounds with the same value, even on the side that has no real constraint
     * — for that example, `upperBound = Bound([5], inclusive = true)`, with no upper limit
     * expressed on col2. Leaving it out of one side silently turns that leading column into an
     * open-ended scan too: with `upperBound = null`, a `col1 = 5 AND col2 >= 10` scan would keep
     * walking straight into `col1 = 6, 7, 8...` because nothing ever told it col1 was supposed to
     * stay at 5.
     *
     * Only a *single* range-bearing column can be expressed this way. A composite index only ever
     * represents one contiguous byte interval, so independent ranges on two columns (`5 < col1 < 10
     * AND 10 < col2 < 20`) describe a rectangle in (col1, col2) space, not an interval — no [Bound]
     * pair can capture that. This function can only narrow the scan using the first range-bearing
     * column; any further per-column condition the index can't express - including one on a column
     * it doesn't even contain - is the [filter] parameter's job (the standard "index scan + filter"
     * / "residual predicate" split real query planners make: whatever a chosen index's key range
     * can't capture becomes a row-by-row filter on top of it).
     *
     * ### How open/closed becomes bytes
     * [Bound.isInclusive] never changes the comparator used while scanning — that stays one fixed
     * `>=`/`<` check per [ScanDirection] (see the loop below). Instead it changes which serializer
     * method builds the boundary bytes: `x <= v` is expressed as `x < successor(v)`, so inclusive
     * vs. exclusive only changes whether [serializeBound] calls `serialize` or `serializeUpper` for
     * that side. See [serializeBound] for the full seek/stop × inclusive/exclusive table.
     *
     * ### [filter], [limit], [offset]
     * Deciding *which* conditions become [lowerBound]/[upperBound] versus [filter] is entirely the
     * caller's (eventually the query planner's) job - this function never checks whether [filter]
     * overlaps with the bounds, since a planner that splits the WHERE clause correctly never hands
     * it a redundant condition in the first place.
     *
     * All three run inside this same locked, single-pass scan rather than as separate steps the
     * caller applies afterward - standard SQL semantics require `WHERE` (here, bounds + [filter])
     * to apply before `LIMIT`/`OFFSET`, and this function never returns a lazy stream a caller could
     * keep pulling from outside the lock (see this doc's first paragraph), so there's nowhere else
     * for that ordering to happen. Applied in sequence: bound check -> [filter] -> [offset] ->
     * [limit]. A dropped ([offset]-skipped or [filter]-rejected) row never pays for row
     * reconstruction it doesn't need, and once [limit] rows are collected, the scan stops pulling
     * from the cursor entirely rather than walking the rest of the range.
     */
    fun selectByRange(
        indexName: String,
        lowerBound: Bound<List<Any?>>,
        upperBound: Bound<List<Any?>>,
        orderBy: List<ColumnOrder>,
        filter: ((Row) -> Boolean)? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Row> {
        readLock.withLock {
            val index = resolveIndex(indexName)
            val keyColumns = index.metadata.keyColumns
            val prefixLen = resolveEqualPrefixLen(lowerBound, upperBound)
            val scanDirection = resolveScanDirection(keyColumns, orderBy, prefixLen)
            val (start, end) = resolveBoundOrder(lowerBound, upperBound, scanDirection)
            val serializedStartBound =
                start.value?.let {
                    serializeBound(
                        index.keySerializer,
                        start.value,
                        scanDirection,
                        start.isInclusive,
                        true,
                    )
                }
            val serializedEndBound =
                end.value?.let {
                    serializeBound(
                        index.keySerializer,
                        end.value,
                        scanDirection,
                        end.isInclusive,
                        false,
                    )
                }
            val cursor =
                index.btree.search(serializedStartBound, scanDirection, start.value != null)
                    ?: return emptyList()
            val result = mutableListOf<Row>()
            logger.info { "$scanDirection $filter $limit $offset" }
            cursor.use { currentCursor ->
                generateSequence { currentCursor.step() }
                    .takeWhile { (currentKey, _) ->
                        serializedEndBound == null ||
                                when (scanDirection) {
                                    ScanDirection.FORWARD ->
                                        Arrays.compareUnsigned(currentKey, serializedEndBound) < 0

                                    ScanDirection.BACKWARD ->
                                        Arrays.compareUnsigned(currentKey, serializedEndBound) >= 0
                                }
                    }
                    .map { (_, currentValue) -> extractData(index, currentValue) }
                    .let { sequence -> if (filter != null) sequence.filter(filter) else sequence }
                    .onEach { row -> logger.info { "before filter: ${row["id"]}" } }
                    .drop(offset ?: 0)
                    .let { sequence -> if (limit != null) sequence.take(limit) else sequence }
                    .forEach { row -> result.add(row) }
            }
            return result.toList()
        }
    }

    /**
     * Convenience wrapper for a pure prefix search: [prefix] is used as both bounds of
     * [selectByRange], always inclusive, since "everything under this prefix" has no meaningful
     * exclusive reading. Delegates entirely to [selectByRange] — see its doc for why prefix search
     * and range scan are the same mechanism underneath.
     */
    fun selectByPrefix(
        indexName: String,
        prefix: List<Any?>,
        orderBy: List<ColumnOrder>,
    ): List<Row> {
        val bound = Bound(prefix, isInclusive = true)
        return selectByRange(indexName, bound, bound, orderBy)
    }

    /**
     * Point lookup through a secondary index: resolves [key] to the primary key it stores, then
     * re-reads the row from the primary index. Returns null if [key] isn't in [indexName] at all (a
     * normal miss); if the secondary hit points at a primary key that no longer exists, that's
     * [extractData] throwing [TableException.CorruptedIndex] instead, since that's an
     * index-consistency failure rather than a plain miss.
     */
    fun selectByIndex(indexName: String, key: List<Any?>): Row? {
        readLock.withLock {
            val index = resolveIndex(indexName)
            val keySerialized = index.keySerializer.serialize(key)
            val searchedPrimaryKey = index.btree.search(keySerialized) ?: return null
            return extractData(index, searchedPrimaryKey)
        }
    }

    /**
     * Scans every row of the table (via the primary index) when no index can narrow the WHERE
     * clause down to a range - again not a separate mechanism, just [selectByRange] with
     * `Bound(null, ...)` on both sides, the same degenerate-case pattern [selectByPrefix] uses.
     * `isInclusive` on that unbounded [Bound] is irrelevant and ignored (see [Bound]'s own doc).
     *
     * @param filter Applied to every scanned row, before [offset]/[limit] - see [selectByRange]'s
     *   doc for why that order matters and why this has to live in the same locked pass as the
     *   scan itself rather than as a separate post-processing step by the caller.
     */
    fun fullScan(
        orderBy: List<ColumnOrder>,
        filter: ((Row) -> Boolean)? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<Row> {
        val bound = Bound<List<Any?>>(null, isInclusive = true)
        return selectByRange(primaryIndex.metadata.indexName, bound, bound, orderBy, filter, limit, offset)
    }

    /**
     * Updates [row] in place, keyed by its (unchanging) primary key.
     *
     * For every secondary index, the value written back is [IndexHandle.valueSerializer]- encoded —
     * the *same* encoding [insertRow] uses and [extractData] expects when resolving a secondary hit
     * back to a row. This function used to reuse the primary key's *key*-serialized bytes here
     * instead (`primaryIndex.keySerializer`, since that value was already sitting in a local
     * variable) — those are a completely different byte layout, and rows written that way looked
     * fine right up until a later [selectByIndex] tried to decode them with
     * [IndexHandle.valueSerializer] and either threw or returned garbage. A test that asserted on
     * the exact bytes written to a mocked secondary btree is what caught it.
     */
    fun updateRow(row: Row) {
        readLock.withLock {
            val primaryTree = primaryIndex.btree
            val primaryKey = primaryIndex.extractKey(row)
            val primaryKeySerialized = primaryIndex.keySerializer.serialize(primaryKey)
            val oldRow =
                primaryTree.search(primaryKeySerialized)?.let { extractData(primaryIndex, it) }
                    ?: throw TableException.RowNotFound(
                        SQLErrorDetail(
                            entityType = EntityType.ROW,
                            tableName = primaryIndex.metadata.tableName,
                        )
                    )

            primaryTree.update(
                primaryKeySerialized,
                primaryKeySerialized,
                primaryIndex.valueSerializer.serialize(row.toList()),
            )

            for ((_, handle) in secondaryIndexes) {
                val oldKey = handle.extractKey(oldRow)
                val oldKeySerialized = handle.keySerializer.serialize(oldKey)
                val newKey = handle.extractKey(row)
                val newKeySerialized = handle.keySerializer.serialize(newKey)
                val indexValueSerialized = handle.valueSerializer.serialize(primaryKey)
                if (oldKeySerialized.contentEquals(newKeySerialized)) {
                    handle.btree.update(oldKeySerialized, oldKeySerialized, indexValueSerialized)
                } else {
                    handle.btree.delete(oldKeySerialized)
                    handle.btree.insert(newKeySerialized, indexValueSerialized)
                }
            }
        }
    }

    /**
     * Deletes the row at [key] from the primary index and every secondary index.
     *
     * The row is read first, because the secondary index keys to remove are derived from its
     * current column values.
     *
     * @throws TableException.RowNotFound if no row exists at [key].
     */
    fun deleteRow(key: List<Any?>) {
        readLock.withLock {
            val primaryTree = primaryIndex.btree
            val keySerialized = primaryIndex.keySerializer.serialize(key)
            val oldRow =
                primaryTree.search(keySerialized)?.let { extractData(primaryIndex, it) }
                    ?: throw TableException.RowNotFound(
                        SQLErrorDetail(
                            entityType = EntityType.ROW,
                            tableName = primaryIndex.metadata.tableName,
                        )
                    )
            for ((_, handle) in secondaryIndexes) {
                val indexKey = handle.extractKey(oldRow)
                val indexKeySerialized = handle.keySerializer.serialize(indexKey)
                handle.btree.delete(indexKeySerialized)
            }
            primaryTree.delete(keySerialized)
        }
    }

    /**
     * Deletes every row matching [lowerBound]/[upperBound]/[filter] on [indexName], and returns how
     * many rows were actually deleted.
     *
     * Parameters mean exactly what they do in [selectByRange] (see its doc for the bound/prefix
     * contract and the [filter] split); a full-table delete is the same degenerate case as
     * [fullScan] - the primary index with `Bound(null, ...)` on both sides. Which index and bounds
     * to use is the caller's (eventually the query planner's) decision, not this function's.
     *
     * ### Two phases
     * Matching rows are first collected into a list via [selectByRange] (which closes its cursor
     * before returning), and only then deleted one by one through [deleteRow]. Deleting while the
     * cursor is still open is not an option: the cursor holds a read latch on its current leaf,
     * which [index.btree.BTree.delete] would need to write-latch.
     *
     * ### Count
     * Each row is deleted via [deleteRow] by primary key, which re-reads it at delete time. A row
     * that vanished between collection and deletion (another thread deleted it first)
     * throws [TableException.RowNotFound]; that is swallowed and not counted, since the row is gone
     * either way. Any other exception propagates. The count is best-effort: [deleteRow]'s lookup
     * and delete are not atomic, so two threads deleting the same row at once can both count it.
     *
     * ### Limitations (no WAL, no row locks)
     * - Not atomic: an exception partway through leaves the earlier rows already deleted.
     * - [filter] and the bounds are evaluated only at collection time. A row updated by another
     *   thread in between, so that it no longer matches, is still deleted; a row inserted in
     *   between is missed.
     */
    fun deleteWhere(
        indexName: String,
        lowerBound: Bound<List<Any?>>,
        upperBound: Bound<List<Any?>>,
        filter: ((Row) -> Boolean)? = null
    ): Int {
        readLock.withLock {
            val targetRows = selectByRange(indexName, lowerBound, upperBound, emptyList(), filter, null, null)
            var affectedRowCount = 0
            targetRows.forEach { row->
                val key = primaryIndex.extractKey(row)
                try{
                    deleteRow(key)
                    affectedRowCount++
                } catch(e: TableException.RowNotFound){
                    logger.debug { "deleteWhere: row already deleted by another thread, skipped (table=${primaryIndex.metadata.tableName}, key=$key)" }
                }
            }
            return affectedRowCount
        }
    }

    /**
     * Builds the boundary bytes for one side ([isStart]: seek vs. stop) of a range scan.
     *
     * The scan's stop condition in [selectByRange] is always a single fixed comparator per
     * [direction] (`>=` for FORWARD, `<` for BACKWARD); open vs. closed is expressed entirely by
     * *which serializer method* builds the boundary here — `serialize` (the value's own encoding)
     * or `serializeUpper` (its successor: the point just past everything sharing that value as a
     * prefix) — using the identity `x <= v` ⟺ `x < successor(v)`.
     *
     * Which one to call depends on [direction] *and* [isStart] together; neither alone decides it:
     *
     * |                | inclusive      | exclusive      |
     * |----------------|----------------|----------------|
     * | FORWARD, seek  | serialize      | serializeUpper |
     * | BACKWARD, seek | serializeUpper | serialize      |
     * | FORWARD, stop  | serializeUpper | serialize      |
     * | BACKWARD, stop | serialize      | serializeUpper |
     *
     * The seek and stop rows are exact mirror images of each other — the same successor that
     * expresses an inclusive *upper* bound for a stop check also expresses an exclusive *lower*
     * bound for a seek, just in the opposite role. That symmetry is why no separate "predecessor"
     * operation is needed anywhere in this design.
     *
     * `serializeUpper` can return null — only when [bound]'s last column is declared DESC and is
     * exactly NULL, meaning that group already sits at the tree's physical right edge and no
     * successor byte sequence exists. Callers pass that null straight through to
     * [index.btree.BTree.search] as the seek key, alongside a separate `boundGiven` flag, exactly
     * the way `BTree.findSearchPosition` expects: `boundGiven` says whether a value existed
     * *before* serialization was attempted at all, while a null [ByteArray] here means
     * serialization was attempted and specifically ran out of successor room — two different
     * questions the tree needs answered separately.
     */
    private fun <K> serializeBound(
        keySerializer: KeySerializer<K>,
        bound: K,
        direction: ScanDirection,
        isInclusive: Boolean,
        isStart: Boolean,
    ): ByteArray? =
        when (isStart) {
            true ->
                when (isInclusive) {
                    true ->
                        when (direction) {
                            ScanDirection.FORWARD -> keySerializer.serialize(bound)
                            ScanDirection.BACKWARD -> keySerializer.serializeUpper(bound)
                        }

                    false ->
                        when (direction) {
                            ScanDirection.FORWARD -> keySerializer.serializeUpper(bound)
                            ScanDirection.BACKWARD -> keySerializer.serialize(bound)
                        }
                }

            false ->
                when (isInclusive) {
                    true ->
                        when (direction) {
                            ScanDirection.FORWARD -> keySerializer.serializeUpper(bound)
                            ScanDirection.BACKWARD -> keySerializer.serialize(bound)
                        }

                    false ->
                        when (direction) {
                            ScanDirection.FORWARD -> keySerializer.serialize(bound)
                            ScanDirection.BACKWARD -> keySerializer.serializeUpper(bound)
                        }
                }
        }

    /**
     * Splits ([lower], [upper]) into (seek-side, stop-side) for this [direction].
     *
     * FORWARD walks the tree byte-ascending, so it seeks off the lower bound and stops at the
     * upper; BACKWARD is the mirror image. [index.btree.BTree.search] only ever needs the seek side
     * — the stop side is never passed into the tree at all, and is instead checked by
     * [selectByRange] itself against each [index.btree.Cursor.step] result.
     */
    private fun <K> resolveBoundOrder(
        lower: Bound<K>,
        upper: Bound<K>,
        direction: ScanDirection,
    ): Pair<Bound<K>, Bound<K>> {
        val start = if (direction == ScanDirection.FORWARD) lower else upper
        val end = if (direction == ScanDirection.FORWARD) upper else lower
        return start to end
    }

    /**
     * Counts how many leading columns [lowerBound] and [upperBound] pin to the exact same value.
     * These are the columns [resolveScanDirection] must skip when checking whether the requested
     * [ColumnOrder] is achievable — a column with only one possible value across the whole scan has
     * no real "direction" to check.
     *
     * [Bound.isInclusive] is deliberately not consulted here: whenever both bounds share a value at
     * the same leading position, tuple-comparison semantics force that column to exactly that value
     * regardless of which side (or neither) is inclusive there — the only way both `(...) >= (v,
     * x)` and `(...) <= (v, y)` can hold at once is for the shared leading column to equal `v`
     * exactly, for any combination of inclusive/ exclusive at that position. A plain value
     * comparison is already correct.
     */
    private fun resolveEqualPrefixLen(
        lowerBound: Bound<List<Any?>>,
        upperBound: Bound<List<Any?>>,
    ): Int =
        (lowerBound.value ?: emptyList())
            .zip(upperBound.value ?: emptyList())
            .takeWhile { (l, u) -> l == u }
            .size

    /**
     * Decides whether this scan can be served as a plain [ScanDirection.FORWARD] or
     * [ScanDirection.BACKWARD] tree walk, given what the caller asked for in [orderBy].
     *
     * DESC columns are bit-flipped only once, at encoding time — by the time bytes reach this
     * layer, byte-ascending order already *is* the index's own declared order, mixed ASC/DESC
     * columns included. So satisfying [orderBy] is purely a matter of checking, column by column
     * past [equalPrefixLen] (columns pinned to one value by equality have no direction to check),
     * whether the caller wants each free column's declared direction as-is or exactly reversed:
     * - every free column matches as declared -> FORWARD
     * - every free column is the exact opposite -> BACKWARD
     * - a mix of both -> not representable by a single tree walk (a B-tree, this one included, can
     *   only walk one direction at a time) -> reject
     *
     * [orderBy] doesn't have to name every free column — SQL's `ORDER BY col2` without `col3` means
     * "don't care how col3 ties break" — but whatever it does name must be an unbroken prefix of
     * the free columns starting from the first one. Naming col3 without col2 first isn't
     * achievable: the tree always groups by col2 before it ever looks at col3, so no walk direction
     * can sort by col3 alone across different col2 groups.
     */
    private fun resolveScanDirection(
        keyColumns: List<IndexColumn>,
        orderBy: List<ColumnOrder>,
        equalPrefixLen: Int,
    ): ScanDirection {
        if (orderBy.isEmpty()) return ScanDirection.FORWARD

        val freeColumns = keyColumns.drop(equalPrefixLen)
        requireOrThrow(orderBy.size <= freeColumns.size) {
            TableException.TooManyOrderColumns(
                SQLErrorDetail(
                    entityType = EntityType.INDEX_KEY_COLUMN,
                    reason = "orderBy가 index의범위 컬럼 개수보다 많음",
                    columnNames = orderBy.map { it.name },
                )
            )
        }

        val relevant = freeColumns.take(orderBy.size)
        val paired = relevant.zip(orderBy)

        requireOrThrow(paired.all { (col, order) -> col.name == order.name }) {
            TableException.OrderColumnMismatch(
                SQLErrorDetail(
                    entityType = EntityType.INDEX_KEY_COLUMN,
                    reason = "orderBy는 index의 범위 컬럼 맨 앞부터 순서대로 지정해야 함(중간 생략 불가)",
                    columnNames = orderBy.map { it.name },
                )
            )
        }

        val allMatch = paired.all { (col, order) -> col.descending == order.descending }
        val allReversed = paired.all { (col, order) -> col.descending != order.descending }

        return when {
            allMatch -> ScanDirection.FORWARD
            allReversed -> ScanDirection.BACKWARD
            else ->
                throw TableException.UnsupportedSortDirection(
                    SQLErrorDetail(
                        entityType = EntityType.INDEX_KEY_COLUMN,
                        reason = "인덱스 선언과 일부만 일치하는 정렬 방향은 지원하지 않음",
                        columnNames = paired.map { (col, _) -> col.name },
                    )
                )
        }
    }

    /**
     * Resolves [indexName] to its [IndexHandle], checking the primary index too — a range or point
     * scan by primary key (`WHERE id BETWEEN ...`) is a perfectly normal request, it's just that
     * the primary index isn't one of the entries in [secondaryIndexes].
     */
    private fun resolveIndex(indexName: String): IndexHandle {
        return secondaryIndexes[indexName]
            ?: run {
                if (primaryIndex.metadata.indexName == indexName) primaryIndex
                else
                    throw TableException.UndefinedIndex(
                        SQLErrorDetail(
                            entityType = EntityType.INDEX,
                            entityName = indexName,
                            tableName = primaryIndex.metadata.tableName,
                        )
                    )
            }
    }

    /**
     * Turns a raw value read from [index]'s btree into a [Row].
     *
     * For the primary index, [value] already *is* the row, [IndexHandle.valueSerializer]- encoded.
     * For a secondary index, [value] is instead the primary key, encoded the same way (matching
     * what [insertRow] and [updateRow] write there) — so it has to be resolved through
     * [primaryIndex] to get the actual row. If that lookup comes back empty, the secondary index is
     * pointing at a primary key that no longer exists — a storage-engine-level inconsistency rather
     * than anything a caller's query could have caused, hence [TableException.CorruptedIndex]
     * rather than [TableException.RowNotFound].
     */
    private fun extractData(index: IndexHandle, value: ByteArray): Row {
        val rowValue =
            if (!index.metadata.isPrimary) {
                val primaryKey = index.valueSerializer.deserialize(value).first
                val primaryKeySerialized = primaryIndex.keySerializer.serialize(primaryKey)
                primaryIndex.btree.search(primaryKeySerialized)
                    ?: throw TableException.CorruptedIndex(
                        SQLErrorDetail(
                            entityType = EntityType.INDEX,
                            entityName = index.metadata.indexName,
                            tableName = primaryIndex.metadata.tableName,
                            reason = "secondary index가 가리키는 primary key가 primary tree에 존재하지 않음",
                        )
                    )
            } else {
                value
            }
        return Row(rowSchema, primaryIndex.valueSerializer.deserialize(rowValue).first)
    }

}
