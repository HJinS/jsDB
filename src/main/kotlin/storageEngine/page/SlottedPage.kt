package storageEngine.page

import config.PageConfig
import index.util.decodeVarInt
import index.util.encodeVarInt
import storageEngine.exception.SlottedPageException
import storageEngine.util.PageHeaderOffset
import storageEngine.util.PageType
import java.nio.ByteBuffer
import java.util.Arrays
import kotlin.text.toHexString


/**
 *
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
 * | 0      | 8     | pageID             | PageID                                            |
 * | 8      | 1     | pageType           | The type of the page(leaf, internal, free list)   |
 * | 9      | 1     | reserved           | Extra space for byte alignment                    |
 * | 10     | 2     | recordCount        | Count of the stored cell(record)                  |
 * | 12     | 2     | freeSpaceStart     | Start point of the free space(=end of slot array) |
 * | 14     | 2     | freeSpaceEnd       | End point of the free space(=start of data area)  |
 * | 16     | 2     | freeSlotHead       | Start point of the free slot array                |
 * | 18     | 6     | reserved           | Extra space for byte alignment                    |
 * | 24     | 8     | parentPageId       | Page id of the parent node.                       |
 * | 32     | 8     | leftSiblingPageId  | Page id of the left sibling node.                 |
 * | 40     | 8     | rightSiblingPageId | Page id of the right sibling node.                |
 * | 48     | 8     | lsn                | Log sequence number for WAL recovery.             |
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
class SlottedPage(
    pageConfig: PageConfig,
    pageId: Long = -1,
    data: ByteBuffer,
    pageType: PageType = PageType.EMPTY
): Page(pageConfig, data, pageId, pageType){


    /**
    * slot 삭제시 미사용 슬롯 등록
    * */
    private fun retrieveFreeSlotId(slotId: Int){
        var slotLocation = PageHeaderOffset.FREE_SLOT_HEAD.offset
        var freeSlotId = data.getShort(slotLocation).toInt()
        while(freeSlotId != -1){
            slotLocation = HEADER_SIZE + freeSlotId * SLOT_SIZE
            freeSlotId = data.getShort(slotLocation).toInt()
        }
        data.putShort(slotLocation, slotId.toShort())
        data.putShort(HEADER_SIZE + slotId * SLOT_SIZE, (-1).toShort())
    }

    /**
     * 사용할 미사용 슬롯 하나 획득
     * */
    private fun getFreeSlotId(): Int{
        val freeSlotId = data.getShort(PageHeaderOffset.FREE_SLOT_HEAD.offset).toInt()
        return freeSlotId.takeIf { it != -1 }?.also { id ->
            val nextFreeSlotId = data.getShort(HEADER_SIZE + id * SLOT_SIZE).toInt()
            data.putShort(PageHeaderOffset.FREE_SLOT_HEAD.offset, nextFreeSlotId.toShort())
        } ?: -1
    }

    private fun getNewSlotId(): Int{
        val newSlotLocation = data.getShort(PageHeaderOffset.FREE_SPACE_START.offset)
        data.putShort(PageHeaderOffset.FREE_SPACE_START.offset, (newSlotLocation + SLOT_SIZE).toShort())
        return (newSlotLocation - HEADER_SIZE) / SLOT_SIZE
    }

    /**
     * slot을 특정 index 에 삽입(shift 필요)
     * @return the slotID of the last record
     * */
    private fun insertSlot(index: Int, offset: Short, length: Short){
        val writeOnlyView = data.duplicate()
        val readOnlyView = data.duplicate()
        readOnlyView.position(index)
        readOnlyView.limit(freeSpaceStart)

        writeOnlyView.position(index + SLOT_SIZE)
        writeOnlyView.put(readOnlyView)
        writeOnlyView.clear()
        writeOnlyView.putShort(index, offset)
        writeOnlyView.putShort(index + 2, length)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun insertRecord(offset: Int, key: ByteArray, value: ByteArray, keyLengthEncoded: ByteArray, valueLengthEncoded: ByteArray){
        logger.info("========================[insertRecord 시작]========================")
        var insertLocation = offset
        logger.info("[insertRecord 삽입] key 길이 삽입 위치 = $insertLocation")
        logger.info("[insertRecord 삽입] 인코딩된 key 길이 = ${keyLengthEncoded.toHexString()}")
        data.put(insertLocation, keyLengthEncoded)
        insertLocation += keyLengthEncoded.size
        logger.info("[insertRecord 삽입] key 삽입 위치 = $insertLocation")
        logger.info("[insertRecord 삽입] 인코딩된 key = ${key.toHexString()}")
        data.put(insertLocation, key)
        insertLocation += key.size
        logger.info("[insertRecord 삽입] value 길이 삽입 위치 = $insertLocation")
        logger.info("[insertRecord 삽입] 인코딩된 value 길이 = ${valueLengthEncoded.toHexString()}")
        data.put(insertLocation, valueLengthEncoded)
        insertLocation += valueLengthEncoded.size
        logger.info("[insertRecord 삽입] value 삽입 위치 = $insertLocation")
        logger.info("[insertRecord 삽입] 인코딩된 value = ${value.toHexString()}")
        data.put(insertLocation, value)
        logger.info("========================[insertRecord 종료]========================")
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getData(slotId: Int): Pair<ByteArray, ByteArray>{
        logger.info("========================[getData 시작]========================")
        val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
        val offset = data.getShort(slotLocation)
        val length = data.getShort(slotLocation + 2)
        logger.info("[getData] 조회할 데이터 offset = $offset")
        logger.info("ㄴ[getData] 조회할 데이터 length = $length")
        logger.info("[getData] 조회할 데이터 slotId = $slotId")
        if(length.toInt() == 0) throw IllegalStateException("No Data")
        // slot 데이터를 가지고 실제 데이터 추출
        // 반만 열린 범위인 것을 주의
        val tempBuffer = data.duplicate()
        tempBuffer.position(offset.toInt())
        val recordData = ByteArray(length.toInt())
        tempBuffer.get(recordData)
        logger.info("[getData] 조회할 실제 데이터 = ${recordData.toHexString()}")
        // 가장 앞에 있는 부분은 key의 길이 정보를 varInt로 인코딩 한 것
        // keyLengthByteLen는 인코딩된 byte 길이를 말함
        // 이 길이 정보를 통해 실제 key 데이터를 추출
        val (keyLength, keyLengthByteLen) = decodeVarInt(recordData, 0)
        logger.info("[getData] 조회할 실제 데이터 중 key 길이 decode 결과 = $keyLength")
        logger.info("[getData] 조회할 실제 데이터 중 key 길이 decode 결과의 byte 기준 길이 = $keyLengthByteLen")
        val key = recordData.slice(keyLengthByteLen until keyLengthByteLen + keyLength).toByteArray()
        logger.info("[getData] 조회할 실제 데이터 중 key =  ${key.toHexString()}")
        // valueLengthByteLen는 인코딩된 byte 길이를 말함
        // 이 길이 정보를 통해 실제 value 데이터를 추출
        val (valueLength, valueLengthByteLen) = decodeVarInt(recordData, keyLengthByteLen + keyLength)
        logger.info("[getData] 조회할 실제 데이터 중 value 길이 decode 결과 = $valueLength")
        logger.info("[getData] 조회할 실제 데이터 중 value 길이 decode 결과의 byte 기준 길이 = $valueLengthByteLen")
        val value = recordData.slice(
            keyLengthByteLen + keyLength + valueLengthByteLen until keyLengthByteLen + keyLength + valueLengthByteLen + valueLength
        ).toByteArray()
        logger.info("[getData] 조회할 실제 데이터 중 value =  ${value.toHexString()}")
        logger.info("========================[getData 종료]========================")
        return key to value
    }

    /**
     * @return the slotID of the last one
     * */
    fun updateData(slotId: Int, key: ByteArray, value: ByteArray): Int{
        deleteData(slotId)
        return insertData(key, value)
    }

    fun binarySearch(key: ByteArray): Int{
        var low = 0
        var high = recordCount - 1
        while(low < high) {
            val mid = (high + low) / 2
            val midKey = getData(mid).first
            val compareResult = Arrays.compareUnsigned(key, midKey)
            when {
                compareResult < 0 -> low = mid + 1
                compareResult > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -(low + 1)

    }

    @OptIn(ExperimentalStdlibApi::class)
    fun insertData(key: ByteArray, value: ByteArray): Int {
        // 1. [공간 확인] 헤더, 슬롯, 데이터가 들어갈 공간이 충분한지 확인
        // (Total Length + Slot Size) <= Free Space
        // ... (생략) ...

        // 2. [위치 찾기] 이진 탐색으로 키가 들어갈 슬롯 인덱스(Insert Index) 검색
        // 만약 이미 키가 존재하면(EQ), 중복 키 처리 정책에 따름 (여기선 덮어쓰기나 에러)
        val searchResult = binarySearch(key)
        val insertIndex = if (searchResult >= 0) {
            // 중복 키 대응은 당장은 처리하지 않음
            throw SlottedPageException.DuplicatedKeyException(pageId, pageType, null)
        } else {
            // 키가 없음 -> 삽입 위치 반환 (binarySearch의 반환값 규칙 활용)
            -(searchResult + 1)
        }

        // 3. [데이터 준비] 직렬화 (VarInt 등 인코딩)
        val keyLengthEncoded = encodeVarInt(key.size)
        val valueLengthEncoded = encodeVarInt(value.size)
        val totalDataLength = keyLengthEncoded.size + key.size + valueLengthEncoded.size + value.size

        // 4. [데이터 쓰기] FreeSpace 포인터 이동 및 데이터 기록
        // 데이터는 페이지 끝에서 앞으로 자라납니다.
        // freeSpaceEnd는 "현재 데이터가 시작되는 지점"을 가리키고 있다고 가정
        val dataOffset = freeSpaceEnd - totalDataLength

        // 실제 데이터 기록 (순서: KeyLen -> Key -> ValLen -> Val)
        insertRecord(dataOffset, key, value, keyLengthEncoded, valueLengthEncoded)

        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (dataOffset - 1).toShort())

        // 5. [슬롯 삽입] 슬롯 배열 정렬 유지 (Shift & Insert)
        insertSlot(insertIndex, dataOffset.toShort(), totalDataLength.toShort())

        // 6. [메타데이터 갱신] 레코드 수 증가 등
        increaseRecordCount()
        return insertIndex
    }




    /**
     * @return the slotID of the last one
     * */
    fun deleteData(slotId: Int): Int{
        val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
        data.putShort(slotLocation, 0)
        data.putShort(slotLocation+2, 0)
        retrieveFreeSlotId(slotId)
        decreaseRecordCount()
        return slotId
    }

    /*
    * 1. write pointer = page 끝
    * 2. read pointer = 첫번째 슬롯의 offset
    *
    * loop
    * 1. write pointer 이동: 기존 write pointer에서 슬롯의 size 만큼 이동
    * 2. read pointer에서 데이터를 읽어 write pointer로 이동
    * 3. 슬롯의 offset 갱신
    * */
    fun compaction(){
        val slotArrayEndBytes = data.getShort(PageHeaderOffset.FREE_SPACE_START.offset).toInt()
        var slotArrayStartBytes = HEADER_SIZE
        val slotArrayTemp = mutableListOf<Triple<Int, Int, Int>>()

        var slotNumber = 0
        // slotArray 데이터를 memory에 로드
        while(slotArrayStartBytes < slotArrayEndBytes){
            var offset = data.getShort(slotArrayStartBytes).toInt()
            val length = data.getShort(slotArrayStartBytes + 2).toInt()
            if(length == 0) offset = 0
            slotArrayTemp.addLast(Triple(slotNumber, offset, length))
            slotArrayStartBytes += SLOT_SIZE
            slotNumber += 1
        }
        // offset 기준 내림차순 -> 끝에서 부터
        slotArrayTemp.sortByDescending { it.second }

        val readOnlyView = data.duplicate()
        val writeOnlyView = data.duplicate()

        var writePointer = pageConfig.pageSize - 1
        slotArrayStartBytes = HEADER_SIZE
        // iterate 하면서 write pointer는 끝에서 사이즈를 통해 점차 내려감
        // read pointer는 slot array 데이터의 offset을 사용
        slotArrayTemp.forEach { (slotNumber, readPointer, length) ->
            if(length == 0 && readPointer == 0) return@forEach
            writePointer -= length
            readOnlyView.clear()
            readOnlyView.position(readPointer)
            readOnlyView.limit(readPointer + length)
            writeOnlyView.clear()
            writeOnlyView.position(writePointer)
            writeOnlyView.put(readOnlyView)
            data.putShort(slotArrayStartBytes + SLOT_SIZE * slotNumber, writePointer.toShort())
        }
        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, writePointer.toShort())
    }

    companion object{
        internal const val HEADER_SIZE = 56
        internal const val SLOT_SIZE: Short = 4
    }
}