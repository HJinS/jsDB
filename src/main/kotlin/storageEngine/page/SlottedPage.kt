package storageEngine.page

import config.IndexConfig
import index.util.decodeVarInt
import index.util.encodeVarInt
import storageEngine.exception.SlottedPageException
import storageEngine.util.PageHeaderOffset
import java.nio.ByteBuffer
import java.util.Arrays


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
open class SlottedPage(
    indexConfig: IndexConfig,
    pageId: Long = -1,
    data: ByteBuffer
): Page(indexConfig, data, pageId){

    /**
     * slot을 특정 index 에 삽입(shift 필요)
     * @return the slotID of the last record
     * */
    private fun insertSlot(index: Int, offset: Short, length: Short){
        val slotLocation = HEADER_SIZE + index * SLOT_SIZE
        if(index < recordCount){
            shiftSlot(index, recordCount - index, 1)
        }
        data.putShort(slotLocation, offset)
        data.putShort(slotLocation + 2, length)
    }

    @OptIn(ExperimentalStdlibApi::class)
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

    @OptIn(ExperimentalStdlibApi::class)
    fun getData(slotId: Int): Pair<ByteArray, ByteArray>{
        val slotLocation = HEADER_SIZE + slotId * SLOT_SIZE
        val offset = data.getShort(slotLocation)
        val length = data.getShort(slotLocation + 2)

        if(length.toInt() == 0) throw SlottedPageException.SlotOutOfBoundException(slotId, pageId, type)
        // slot 데이터를 가지고 실제 데이터 추출
        // 반만 열린 범위인 것을 주의
        val tempBuffer = data.duplicate()
        tempBuffer.position(offset.toInt())
        val recordData = ByteArray(length.toInt())
        tempBuffer.get(recordData)

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

    /**
     * @return the slotID of the last one
     * */
    fun updateData(slotId: Int, key: ByteArray, value: ByteArray): Int{
        deleteData(slotId)
        return insertData(slotId, key, value)
    }

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
     * SLOT_SIZE(4 Byte) 단위로 slot을 옮김.
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
            throw SlottedPageException.SlotShiftException(pageId, type, e)
        }
        return src
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun insertData(slotId: Int, key: ByteArray, value: ByteArray): Int {
        // 1. [공간 확인] 헤더, 슬롯, 데이터가 들어갈 공간이 충분한지 확인
        // (Total Length + Slot Size) <= Free Space
        // ... (생략) ...

        // 3. [데이터 준비] 직렬화 (VarInt 등 인코딩)
        val keyLengthEncoded = encodeVarInt(key.size)
        val valueLengthEncoded = encodeVarInt(value.size)
        val totalDataLength = keyLengthEncoded.size + key.size + valueLengthEncoded.size + value.size
        val needed = totalDataLength + SLOT_SIZE

        if (freeSpace < needed) {
            compaction()
            if (freeSpace < needed) throw SlottedPageException.PageFullException(totalDataLength, pageId)
        }

        // 4. [데이터 쓰기] FreeSpace 포인터 이동 및 데이터 기록
        // 데이터는 페이지 끝에서 앞으로 자라납니다.
        // freeSpaceEnd는 "현재 데이터가 시작되는 지점"을 가리키고 있다고 가정
        val dataOffset = freeSpaceEnd - totalDataLength + 1

        // 실제 데이터 기록 (순서: KeyLen -> Key -> ValLen -> Val)
        insertRecord(dataOffset, key, value, keyLengthEncoded, valueLengthEncoded)

        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (dataOffset - 1).toShort())

        // 5. [슬롯 삽입] 슬롯 배열 정렬 유지 (Shift & Insert)
        insertSlot(slotId, dataOffset.toShort(), totalDataLength.toShort())

        // 6. [메타데이터 갱신] 레코드 수 증가 등
        increaseRecordCount()
        return slotId
    }

    /**
     * @return the slotID of the last one
     * */
    fun deleteData(slotId: Int): Pair<ByteArray, ByteArray>{
        val (key, value) = getData(slotId)
        if(slotId < recordCount - 1){
            shiftSlot(slotId+1, recordCount -  (slotId + 1), -1)
        }
        decreaseRecordCount()
        return key to value
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
            if(length > 0){
                slotArrayTemp.add(Triple(slotNumber, offset, length))
            }
            slotArrayStartBytes += SLOT_SIZE
            slotNumber += 1
        }
        // offset 기준 내림차순 -> 끝에서 부터
        slotArrayTemp.sortByDescending { it.second }

        val readOnlyView = data.duplicate()
        val writeOnlyView = data.duplicate()

        var writePointer = indexConfig.pageSize
        // iterate 하면서 write pointer는 끝에서 사이즈를 통해 점차 내려감
        // read pointer는 slot array 데이터의 offset을 사용
        
        var idx = 0
        while(idx < slotArrayTemp.size){
            var totalCopyLength = slotArrayTemp[idx].third
            // 연속된 그룹의 가장 바깥에 있는 slot의 array idx
            val copyGroupStartIdx = idx

            // while loop를 사용해 한번에 묶어서 복사할 연속된 slot을 찾음
            // idx가 커지면, slot 위치상 가장 안쪽임(offset 기존 내림차순 이기 때문)
            while(idx+1 < slotArrayTemp.size && slotArrayTemp[idx].second == slotArrayTemp[idx+1].second + slotArrayTemp[idx+1].third){
                totalCopyLength += slotArrayTemp[idx+1].third
                idx++
            }

            val groupCopyReadStart = slotArrayTemp[idx].second
            // 길이만큼 빼서 writePointer지점은 연속된 group의 가장 안쪽 slot임(데이터 위치상 가장 안쪽)
            writePointer -= totalCopyLength
            // DirectByteBuffer.put(ByteBuffer)는 내부적으로 UNSAFE.copyMemory(= memcpy)를 사용하기 때문에
            // src와 dst가 같은 네이티브 메모리를 공유하면서 dst > src인 경우(위쪽으로 이동)
            // 겹치는 구간의 데이터가 덮어씌워져 오염된다.
            // compaction은 freeSpace < needed일 때만 호출되므로, 특정 페이지가 거의 꽉 찼을 때만
            // 발생한다. 테스트가 랜덤 셔플 데이터를 쓰거나 삭제-삽입 패턴이 맞아떨어질 때만
            // 해당 조건에 걸려서 버그가 간헐적으로 나타났다.
            // ByteArray를 중간에 거치면 네이티브 메모리 → JVM 힙 → 네이티브 메모리 순서로 복사되므로
            // src/dst overlap이 불가능해져 안전하다.
            if (writePointer != groupCopyReadStart) {
                val groupData = ByteArray(totalCopyLength)
                readOnlyView.clear()
                readOnlyView.position(groupCopyReadStart)
                readOnlyView.get(groupData)
                writeOnlyView.position(writePointer)
                writeOnlyView.put(groupData)
            }

            var currentWritePointer = writePointer
            // slot에 offset 업데이트, slot배열 은 변경점 없음
            for(idx2 in idx downTo copyGroupStartIdx){
                val slotIdx = slotArrayTemp[idx2].first
                data.putShort(HEADER_SIZE + slotIdx * SLOT_SIZE, currentWritePointer.toShort())
                currentWritePointer += slotArrayTemp[idx2].third
            }
            idx++

        }

        data.putShort(PageHeaderOffset.FREE_SPACE_END.offset, (writePointer-1).toShort())
    }

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
