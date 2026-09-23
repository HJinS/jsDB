package storageEngine.page

import config.IndexConfig
import util.decodeVarInt
import util.encodeVarInt
import exception.StorageEngineException
import util.EngineErrorDetail
import util.INVALID_PAGE_ID
import util.PageHeaderOffset
import java.nio.ByteBuffer
import java.util.Arrays


/**
 *
 * Header
 * 1. Initializes a new page into a given node type's slotted-page structure.
 * 2. Reads/sets the page type.
 * 3. Sets/reads the number of records stored on the page.
 * 4. Sets/reads the next sibling leaf's page id (leaf only).
 *
 * Records
 * 1. Inserts a variable-length record into the page's free space -> returns that record's slot number.
 * 2. Deletes the record at a given slot number.
 * 3. Returns the ByteArray of the record at a given slot number.
 * 4. Updates the record at a given slot with new data (moves it internally if the size changes).
 *
 * Page state and space management
 * 1. Returns the amount of free space remaining on the page.
 * 2. Cleans up space fragmented by deletions etc. to reclaim contiguous free space.
 *
 * | offset | bytes | fieldName          | description                                       |
 * |--------|-------|--------------------|---------------------------------------------------|
 * | 0      | 8     | pageID             | PageID                                            |
 * | 8      | 1     | pageType           | The type of the page(leaf, internal, free list)   |
 * | 9      | 1     | reserved           | Extra space for byte alignment                    |
 * | 10     | 2     | recordCount        | Count of the stored cell(record)                  |
 * | 12     | 2     | freeSpaceStart     | Start point of the free space(=end of slot array) |
 * | 14     | 2     | freeSpaceEnd       | End point of the free space(=start of data area)  |
 * | 16     | 2     | reserved           | Unused (was a free-slot-list head; see BUG-028)   |
 * | 18     | 6     | reserved           | Extra space for byte alignment                    |
 * | 24     | 8     | parentPageId       | Page id of the parent node.                       |
 * | 32     | 8     | leftSiblingPageId  | Leaf: left sibling.                               |
 * | 40     | 8     | rightSiblingPageId | Page id of the right sibling node (leaf only).    |
 * | 48     | 8     | lsn                | Reserved for a future WAL; always 0, never read.  |
 *
 *
 * leftSiblingPageId
 * - Leaf: left sibling.
 * - Internal: leftmost child (see [Page.leftMostChildPageId]).
 * - Freed page: free-list "next" pointer (see [storageEngine.FreeSpaceManager]). 
 * ```
 * Initial state
 * +-------------------------------------------------+
 * |                    Page Header                  |
 * |         [Record count: 3] freeSpaceEnd          |
 * +-------------------------------------------------+ <--- Slot array start.
 * |                        ↓                        |
 * +-------------------------------------------------+ <--- Slot array end(freeSpaceStart).
 * |                                                 |
 * |                    Free Space                   |
 * |                                                 |
 * +-------------------------------------------------+ <--- Data start(freeSpaceEnd).
 * |                        ↑                        |
 * +-------------------------------------------------+
 *
 * After insert.
 * +-------------------------------------------------+
 * |                    Page Header                  |
 * |         [Record count: 3] freeSpaceEnd          |
 * +-------------------------------------------------+ <--- Slot array start.
 * |         Slot 1: [Record 1 offset, size]         |
 * |         Slot 2: [Record 2 offset, size]         |
 * |         Slot 3: [Record 3 offset, size]         |
 * +-------------------------------------------------+ <--- Slot array end(freeSpaceStart).
 * |                                                 |
 * |                    Free Space                   |
 * |                                                 |
 * +-------------------------------------------------+ <--- Data start(freeSpaceEnd).
 * |                     Record 3                    |
 * |                     Record 2                    |
 * |                     Record 1                    |
 * +-------------------------------------------------+
 * ```
 * */
open class SlottedPage(
    indexConfig: IndexConfig,
    pageId: Long = INVALID_PAGE_ID,
    data: ByteBuffer
): Page(indexConfig, data, pageId){

    /**
     * Inserts one slot entry `(offset, length)` at [index], shifting every slot from [index]
     * onward one position later first if [index] isn't already past the end (keeps the slot array
     * — hence key order — contiguous and sorted; see [shiftSlot]).
     * */
    private fun insertSlot(index: Int, offset: Short, length: Short){
        val slotLocation = HEADER_SIZE + index * SLOT_SIZE
        if(index < recordCount){
            shiftSlot(index, recordCount - index, 1)
        }
        data.putShort(slotLocation, offset)
        data.putShort(slotLocation + 2, length)
    }

    /**
     * Writes one record's raw bytes at [offs
     * - `keyLen | key | valueLen | value`, back-to-back.
     * */
    private fun insertRecord(offset: Int, key: ByteArray, value: ByteArray, keyLengthEncoded: ByteArray, valueLengthEncoded: ByteArray){
        var insertLocation = offset
        data.put(insertLocation, keyLengthEncoded)
        insertLocation += keyLengthEncoded.size

        data.put(insertLocation, key)
        insertLocation += key.size

        data.put(insertLocation, valueLengthEncoded)
        insertLocation += valueLengthEncoded.size

        data.put(insertLocation, value)
    }

    /**
     * Reads the record at [slotId]
     * - looks up its `(offset, length)` in the slot array, then parses
     * - the `keyLen | key | valueLen | value` layout [insertRecord] wrote.
     *
     * @throws StorageEngineException.SlotOutOfBound if [slotId] is outside `0..<recordCount`, or 
     * if the slot's `length` is 0 — a slot number that's structurally in range but was never actually written.
     * - (bounds check added in BUG-019, see `history/bugs/`)

     * */
    fun getData(slotId: Int): Pair<ByteArray, ByteArray>{
        if(slotId !in 0..<recordCount)
            throw StorageEngineException.SlotOutOfBound(
                EngineErrorDetail(
                    pageId = pageId,
                    pageType = type,
                    reason = "No more data. slotID: $slotId"
                )
            )
        val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
        val offset = data.getShort(slotLocation)
        val length = data.getShort(slotLocation + 2)

        if(length.toInt() == 0)
            throw StorageEngineException.SlotOutOfBound(
                EngineErrorDetail(
                    pageId = pageId,
                    pageType = type,
                    reason = "No more data. slotID: $slotId"
                )
            )
        // Extract the actual data using the slot info.
        // Note: this is a half-open range.
        val tempBuffer = data.duplicate()
        tempBuffer.position(offset.toInt())
        val recordData = ByteArray(length.toInt())
        tempBuffer.get(recordData)

        // The very front is the key's length, varint-encoded.
        // keyLengthByteLen is how many bytes that encoding itself took up.
        // Use that length to extract the actual key data.
        val (keyLength, keyLengthByteLen) = decodeVarInt(recordData, 0)
        val key = recordData.slice(keyLengthByteLen until keyLengthByteLen + keyLength).toByteArray()

        // valueLengthByteLen is how many bytes the value-length encoding took up.
        // Use that length to extract the actual value data.
        val (valueLength, valueLengthByteLen) = decodeVarInt(recordData, keyLengthByteLen + keyLength)

        val value = recordData.slice(
            keyLengthByteLen + keyLength + valueLengthByteLen
                    until
                    keyLengthByteLen + keyLength + valueLengthByteLen + valueLength
        ).toByteArray()
        return key to value
    }

    /**
     * Replaces the record at [slotId] with ([key], [value]) — implemented as delete-then-insert
     * rather than in-place, since the new record's encoded length may differ from the old one's.
     *
     * @return [slotId] itself, echoed back for chaining (matches [insertData]'s return contract).
     * */
    fun updateData(slotId: Int, key: ByteArray, value: ByteArray): Int{
        deleteData(slotId)
        return insertData(slotId, key, value)
    }

    /**
     * Standard binary search over the slot array by key bytes.
     *
     * @return The matching slot index, or `-(insertionPoint + 1)` if [key] isn't present
     * - Kotlin/Java `Collections.binarySearch` convention.
     * */
    fun binarySearch(key: ByteArray): Int{
        var low = 0
        var high = recordCount - 1
        while(low <= high) {
            val mid = (high + low) / 2
            val midKey = getData(mid).first
            val compareResult = Arrays.compareUnsigned(key, midKey)
            when {
                compareResult < 0 -> high = mid - 1
                compareResult > 0 -> low = mid + 1
                else -> return mid
            }
        }
        return -(low + 1)
    }

    /**
     * Moves slots in SLOT_SIZE(4 byte) units
     * - shifts the range `[src, src+srcLength)` by `shiftLength` slots.
     *
     * Safe even when the src/dst ranges overlap
     * - the whole range is first copied into a plain JVM
     * - heap array ([temp]) before being written back, so the overlap corruption that `ByteBuffer.put`
     * - can cause via its internal memcpy (BUG-010) can't happen in the first place.
     * */
    private fun shiftSlot(src: Int, srcLength: Int, shiftLength: Int): Int {
        if (shiftLength == 0 || srcLength <= 0) return -1
        
        try {
            val srcOffset = HEADER_SIZE + (src * SLOT_SIZE)
            val srcLengthByte = srcLength * SLOT_SIZE
            val dstOffset = srcOffset + (shiftLength * SLOT_SIZE)

            val temp = ByteArray(srcLengthByte)
            val readView = data.duplicate()
            readView.position(srcOffset)
            readView.get(temp)

            val writeView = data.duplicate()
            writeView.position(dstOffset)
            writeView.put(temp)
        } catch (e: Exception) {
            throw StorageEngineException.SlotShift(
                EngineErrorDetail(
                    pageId = pageId,
                    pageType = type,
                    reason = "Invalid shift count."
                ), e
            )
        }
        return src
    }

    /**
     * Inserts a new record at [slotId] (shifting later slots later, see [insertSlot]).
     * If there isn't enough contiguous free space, tries [compaction] once before giving up with [StorageEngineException.PageFull].
     *
     * @return [slotId] itself, echoed back.
     * */
    fun insertData(slotId: Int, key: ByteArray, value: ByteArray): Int {
        // 1. [Check space] Confirm there's enough room for the header, slot, and data.
        // (Total Length + Slot Size) <= Free Space
        // ... (details below) ...

        // 3. [Prepare data] Serialize (VarInt encoding, etc.)
        val keyLengthEncoded = encodeVarInt(key.size)
        val valueLengthEncoded = encodeVarInt(value.size)
        val totalDataLength = keyLengthEncoded.size + key.size + valueLengthEncoded.size + value.size
        val needed = totalDataLength + SLOT_SIZE

        if (freeSpace < needed) {
            compaction()
            if (freeSpace < needed)
                throw StorageEngineException.PageFull(
                    EngineErrorDetail(
                        pageId = pageId,
                        reason = "Page full maybe too large record data: $totalDataLength"
                    )
                )
        }

        // 4. [Write data] Move the FreeSpace pointer and write the data.
        // Data grows from the end of the page toward the front.
        // Assumes freeSpaceEnd points at "where the current data starts".
        val dataOffset = freeSpaceEnd - totalDataLength + 1

        // Actually write the data (order: KeyLen -> Key -> ValLen -> Val).
        insertRecord(dataOffset, key, value, keyLengthEncoded, valueLengthEncoded)

        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (dataOffset - 1).toShort())

        // 5. [Insert slot] Keep the slot array sorted (shift & insert).
        insertSlot(slotId, dataOffset.toShort(), totalDataLength.toShort())

        // 6. [Update metadata] Bump the record count, etc.
        increaseRecordCount()
        return slotId
    }

    /**
     * Removes the record at [slotId], shifting every later slot one position earlier to keep the
     * slot array contiguous (see BUG-028 in `history/bugs/` for why this replaced an earlier
     * tombstone/free-list scheme). Does not run [compaction] on the data area — the vacated bytes
     * there become fragmented free space, reclaimed later by [insertData]'s compaction-on-demand.
     *
     * @return The key and value that were stored at [slotId].
     * */
    fun deleteData(slotId: Int): Pair<ByteArray, ByteArray>{
        val (key, value) = getData(slotId)
        if(slotId < recordCount - 1){
            shiftSlot(slotId+1, recordCount -  (slotId + 1), -1)
        }
        decreaseRecordCount()
        return key to value
    }

    /**
     * Defragments the data area
     * [deleteData] leaves gaps between live records without moving
     * them, so free space accumulates as scattered holes rather than one contiguous region.
     * This repacks every live record toward the page's end (in descending-offset groups, batching
     * adjacent ones into a single copy) and updates each slot's offset to match, turning all the
     * scattered free space back into one contiguous block at [freeSpaceEnd].
     *
     * 1. write pointer = end of page
     * 2. read pointer = first slot's offset
     *
     * loop
     * 1. Move write pointer: from the current write pointer, back up by the slot's size.
     * 2. Read data at the read pointer and move it to the write pointer.
     * 3. Update the slot's offset.
     * */
    fun compaction(){
        val slotArrayEndBytes = data.getShort(PageHeaderOffset.FREE_SPACE_START.offset).toInt()
        var slotArrayStartBytes = HEADER_SIZE
        val slotArrayTemp = mutableListOf<Triple<Int, Int, Int>>()

        var slotNumber = 0
        // Load the slot array data into memory.
        while(slotArrayStartBytes < slotArrayEndBytes){
            var offset = data.getShort(slotArrayStartBytes).toInt()
            val length = data.getShort(slotArrayStartBytes + 2).toInt()
            if(length == 0) offset = 0
            if(length > 0){
                slotArrayTemp.add(Triple(slotNumber, offset, length))
            }
            slotArrayStartBytes += SLOT_SIZE
            slotNumber += 1
        }
        // Sort by offset descending -> process from the end of the page.
        slotArrayTemp.sortByDescending { it.second }

        val readOnlyView = data.duplicate()
        val writeOnlyView = data.duplicate()

        var writePointer = indexConfig.pageSize
        // As we iterate, the write pointer steps down from the end by each record's size.
        // The read pointer uses the offset stored in the slot array data.

        var idx = 0
        while(idx < slotArrayTemp.size){
            var totalCopyLength = slotArrayTemp[idx].third
            // Array index of the outermost slot in this contiguous group.
            val copyGroupStartIdx = idx

            // Use a while loop to find a run of contiguous slots to copy together in one go.
            // As idx increases, the slot sits further "inward" positionally (since we sorted by offset descending).
            while(idx+1 < slotArrayTemp.size && slotArrayTemp[idx].second == slotArrayTemp[idx+1].second + slotArrayTemp[idx+1].third){
                totalCopyLength += slotArrayTemp[idx+1].third
                idx++
            }

            val groupCopyReadStart = slotArrayTemp[idx].second
            // Subtract the total length so writePointer lands on the innermost slot of this contiguous group (innermost in data position too).
            writePointer -= totalCopyLength
            // DirectByteBuffer.put(ByteBuffer) uses UNSAFE.copyMemory (= memcpy) internally, so
            // when src and dst share the same native memory and dst > src (moving "upward"),
            // the overlapping region gets corrupted by being overwritten mid-copy.
            // compaction is only called when freeSpace < needed, so this only happens when a
            // page is nearly full. It surfaced intermittently only when a test used randomly
            // shuffled data or happened to hit a delete-then-insert pattern that triggered this
            // condition.
            // Routing through a ByteArray in between forces the copy to go native memory -> JVM
            // heap -> native memory, which makes src/dst overlap impossible and is therefore safe.
            if (writePointer != groupCopyReadStart) {
                val groupData = ByteArray(totalCopyLength)
                readOnlyView.clear()
                readOnlyView.position(groupCopyReadStart)
                readOnlyView.get(groupData)
                writeOnlyView.position(writePointer)
                writeOnlyView.put(groupData)
            }

            var currentWritePointer = writePointer
            // Update each slot's offset; the slot array's own layout doesn't change.
            for(idx2 in idx downTo copyGroupStartIdx){
                val slotIdx = slotArrayTemp[idx2].first
                data.putShort(HEADER_SIZE + slotIdx * SLOT_SIZE, currentWritePointer.toShort())
                currentWritePointer += slotArrayTemp[idx2].third
            }
            idx++

        }

        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (writePointer-1).toShort())
    }

    /**
     * Total bytes an ([key], [value]) insert would need
     * - encoded record bytes plus one slot entry.
     * */
    fun getRequiredSpace(key: ByteArray, value: ByteArray): Int{
        val keyLengthEncoded = encodeVarInt(key.size)
        val valueLengthEncoded = encodeVarInt(value.size)
        return keyLengthEncoded.size + key.size + valueLengthEncoded.size + value.size + SLOT_SIZE
    }

    companion object{
        internal const val HEADER_SIZE = 56
        internal const val SLOT_SIZE: Short = 4
    }
}
