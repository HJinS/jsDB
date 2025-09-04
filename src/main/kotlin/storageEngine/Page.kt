package storageEngine

import index.util.decodeVarInt
import index.util.encodeVarInt
import storageEngine.util.PageHeaderOffset
import storageEngine.util.PageType
import java.nio.ByteBuffer


/**
 * TODO
 * 헤더
 * 1. 새 페이지를 특정 노드 타입의 슬롯 페이지 구조로 초기화
 * 2. 페이지 타입 조회 및 설정
 * 3. 페이지에 저장된 레코드의 개수를 설정 및 조회
 * 4. 다음 형제 리프 노드의 페이지 ID를 설정하거나 읽어오기(리프 전용)
 *
 * 레코드
 * 1. 가변 길이의 레코드를 페이지의 빈 공간에 삽입 -> 해당 레코드의 슬롯 번호 반환
 * 2. 특정 슬롯 번호에 있는 레코드를 삭제 처리
 * 3. 특정 슬롯 번호에 해당하는 레코드의 ByteArray 반환
 * 4. 특정 슬롯의 레코드를 새로운 데이터로 갱신(크기가 변할 경우 내부적으로 이동 처리)
 *
 * 페이지 상태 및 공간 관리
 * 1. 페이지 안에 남아있는 빈 공간의 크기 반환
 * 2. 삭제 등으로 인해 생긴 파편화된 공간을 정리하여 빈 공간을 확보
 *
 * | offset | bytes | fieldName          | description                                       |
 * |--------|-------|--------------------|---------------------------------------------------|
 * | 0      | 1     | pageType           | The type of the page(leaf, internal, free list)   |
 * | 1      | 1     | reserved           | Extra space for byte alignment                    |
 * | 2      | 2     | recordCount        | Count of the stored cell(record)                  |
 * | 4      | 2     | freeSpaceStart     | Start point of the free space(=end of slot array) |
 * | 6      | 2     | freeSpaceEnd       | End point of the free space(=start of data area)  |
 * | 8      | 8     | parentPageId       | Page id of the parent node.                       |
 * | 16     | 8     | leftSiblingPageId  | Page id of the left sibling node.                 |
 * | 24     | 8     | rightSiblingPageId | Page id of the right sibling node.                |
 * | 32     | 8     | lsn                | Log sequence number for WAL recovery.             |
 *
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
 * |         Slot 1: [Record 1 position, size]       |
 * |         Slot 2: [Record 2 position, size]       |
 * |         Slot 3: [Record 3 position, size]       |
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
class Page(
    val pageId: Long,
    val pageSize: Int = 4096,
    pageType: PageType = PageType.LEAF_NODE,
){
    private val data: ByteArray = ByteArray(pageSize)

    // Do not use a relative path function. The data will be saved incorrectly.
    private val buffer: ByteBuffer by lazy {
        ByteBuffer.wrap(data)
    }
    init {
        val buffer: ByteBuffer = ByteBuffer.wrap(data)
        buffer.put(PageHeaderOffset.PAGE_TYPE.offset, pageType.value.toByte())
        buffer.put(PageHeaderOffset.RESERVED.offset, 0)
        buffer.putShort(PageHeaderOffset.RECORD_COUNT.offset, 0)
        buffer.putShort(PageHeaderOffset.FREE_SPACE_START.offset, HEADER_SIZE.toShort())
        buffer.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (pageSize-1).toShort())
        buffer.putLong(PageHeaderOffset.PARENT_PAGE_ID.offset, 0)
        buffer.putLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset, 0)
        buffer.putLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset, 0)
        buffer.putLong(PageHeaderOffset.LSN.offset, 0)
    }
    var type: PageType
        get() = PageType.fromValue(data[0].toShort()) ?: throw IllegalStateException("Page type should be set")
        set(type) {
            data[0] = type.value.toByte()
        }

    val recordCount: Int
        get() = buffer.getShort(2).toInt()

    private val freeSpaceEnd: Int
        get() = buffer.getShort(PageHeaderOffset.FREE_SPACE_END.offset).toInt()

    val leftSiblingPageId: Long
        get() = buffer.getLong(PageHeaderOffset.LEFT_SIBLING_PAGE_ID.offset)

    val rightSiblingPageId: Long
        get() = buffer.getLong(PageHeaderOffset.RIGHT_SIBLING_PAGE_ID.offset)

    private fun insertSlot(offset: Short, length: Short): Int{
        val currentFreeSpaceStart = buffer.getShort(PageHeaderOffset.FREE_SPACE_START.offset).toInt()
        val recordCount = buffer.getShort(PageHeaderOffset.RECORD_COUNT.offset)
        // slotID 는 0 부터 시작
        // 따라서 recordCount 그대로 반환
        // 반환값은 slotID
        buffer.putShort(currentFreeSpaceStart, offset)
        buffer.putShort(currentFreeSpaceStart + 2, length)
        buffer.putShort(PageHeaderOffset.FREE_SPACE_START.offset, (currentFreeSpaceStart + SLOT_SIZE).toShort())
        buffer.putShort(PageHeaderOffset.RECORD_COUNT.offset, (recordCount + 1).toShort())
        return recordCount.toInt()
    }

    fun insertRecord(key: ByteArray, value: ByteArray): Int{
        val buffer = ByteBuffer.wrap(data)
        val keyLength = key.size
        val valueLength = value.size
        val keyLengthEncoded = encodeVarInt(keyLength)
        val valueLengthEncoded = encodeVarInt(valueLength)
        val totalLength = keyLength + valueLength + keyLengthEncoded.size + valueLengthEncoded.size
        val freeSpaceEnd = buffer.getShort(PageHeaderOffset.FREE_SPACE_END.offset).toInt()
        // 0부터 시작하는 특성 상 + 1을 해줘야 위치가 맞음
        // 자라나는 방향이 반대인 것을 잊지 말것
        val initialInsertOffset = freeSpaceEnd - totalLength + 1
        var insertOffset = initialInsertOffset
        buffer.put(insertOffset, keyLengthEncoded)
        insertOffset += keyLengthEncoded.size
        buffer.put(insertOffset, key)
        insertOffset += keyLength
        buffer.put(insertOffset, valueLengthEncoded)
        insertOffset += valueLengthEncoded.size
        buffer.put(insertOffset, value)
        return insertSlot(initialInsertOffset.toShort(), totalLength.toShort())
    }

    fun getRecord(slotId: Int): Pair<ByteArray, ByteArray>{
        val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
        val offset = buffer.getShort(slotLocation)
        val length = buffer.getShort(slotLocation + 2)
        // slot 데이터를 가지고 실제 데이터 추출
        // 반만 열린 범위인 것을 주의
        val recordData = data.slice(offset until (offset+length)).toByteArray()
        // 가장 앞에 있는 부분은 key의 길이 정보를 varInt로 인코딩 한 것
        // keyLengthByteLen는 인코딩된 byte 길이를 말함
        // 이 길이 정보를 통해 실제 key 데이터를 추출
        val (keyLength, keyLengthByteLen) = decodeVarInt(recordData, 0)
        val key = recordData.slice(keyLengthByteLen until keyLengthByteLen + keyLength).toByteArray()
        // valueLengthByteLen는 인코딩된 byte 길이를 말함
        // 이 길이 정보를 통해 실제 value 데이터를 추출
        val (valueLength, valueLengthByteLen) = decodeVarInt(recordData, keyLengthByteLen + keyLength)
        val value = recordData.slice(
            keyLengthByteLen + keyLength + valueLengthByteLen until keyLengthByteLen + keyLength + valueLengthByteLen + valueLength
        ).toByteArray()
        return key to value
    }

    companion object{
        internal const val HEADER_SIZE = 40
        internal const val SLOT_SIZE: Short = 4
    }
}