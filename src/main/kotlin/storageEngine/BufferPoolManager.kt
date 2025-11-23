/**
 * frame table
 * - pageID, frameID 매핑
 * - pageID 로부터 frameID 를 얻어내기 위함
 * buffer pool
 * - frameID 를 가지고 실제 frame 을 보관
 * node map
 * - lru 를 관기하기 위함
 * - frame 접근을 바로 하기 위함
 * doubly linked list
 * - lru linked list
 * - new, old 로 나뉘어 져 있음
 *   - midpoint 를 두어 new, old 를 구분
 *   - old - 37, new 63
 *
 *
 * 주요 기능
 * - 페이지 캐싱
 * - fetchPage
 * - 페이지 교체
 * - dirty page 관리
 *   - write on eviction
 *   - write on shutdown
 *   - background thread
 *
 *
 * BufferPool
 * Frame
 * LRU
 * StorageManager
 * */


class BufferPoolManager(){
    fun fetchPage(){

    }

    fun newPage(){

    }

    fun unpinPage(){

    }

    fun flushPage(){

    }

}