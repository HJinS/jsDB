package storageEngine


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
 * 위치(Offset)	크기(Bytes)      필드 이름        	    설명
 * 0	            1	        pageType	        페이지 종류 (내부/리프/프리리스트 등)
 * 1	            1	        (예약)        	    정렬(alignment)을 위한 여유 공간
 * 2	            2	        cellCount	        저장된 셀(레코드)의 개수
 * 4	            2	        freeSpaceStart	    빈 공간의 시작 위치 (슬롯 배열의 끝)
 * 6	            2	        freeSpaceEnd	    빈 공간의 끝 위치 (데이터 영역의 시작)
 * 8	            8	        parentPageId	    부모 노드의 페이지 ID (4바이트)
 * 16	            8	        leftSiblingPageId  (리프 전용) 오른쪽 형제 페이지 ID (4바이트)
 * 24	            8	        rightSiblingPageId  (리프 전용) 오른쪽 형제 페이지 ID (4바이트)
 * 32	            8	        lsn	                (고급) 로그 시퀀스 번호. WAL 복구 시 사용
 *
 *
 *
 * +-------------------------------------------------+
 * |                    Page Header                  |
 * |         [Record count: 3] [freeSpaceEnd         |
 * +-------------------------------------------------+ <--- Slot array start.
 * |         Slot 1: [Record 1 position, size]       |
 * |         Slot 2: [Record 2 position, size]       |
 * |         Slot 3: [Record 3 position, size]       |
 * +-------------------------------------------------+ <--- Slot array end.
 * |                                                 |
 * |                    Free Space                   |
 * |                                                 |
 * +-------------------------------------------------+ <--- Data start.
 * |                     Record 3                    |
 * |                     Record 2                    |
 * |                     Record 1                    |
 * +-------------------------------------------------+
 * */
class Page(
    val pageId: Long,
    val pageSize: Int
){
    internal val data: ByteArray = ByteArray(pageSize)

    init {

    }
}