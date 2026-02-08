import config.PageConfig
import storageEngine.DiskManager
import storageEngine.lru.MidpointLRUPolicy
import storageEngine.page.Frame
import storageEngine.page.PageHandle
import java.util.concurrent.locks.ReentrantLock

/**
 * frame table
 * - pageID, frameID 매핑
 * - pageID 로부터 frameID 를 얻어내기 위함
 * buffer pool
 * - frameID 를 가지고 실제 frame 을 보관
 * node map
 * - lru 를 관리하기 위함
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


class BufferPoolManager(
    private val diskManager: DiskManager,
    private val replacer: MidpointLRUPolicy,
    pageConfig: PageConfig,
    poolSize: Int
){
    private val frames: Array<Frame> = Array(poolSize) { Frame(it, null, pageConfig.pageSize) }
    private val pageTable = HashMap<Long, Int>()
    private val freeList = ArrayDeque<Int>()
    private val globalLatch = ReentrantLock()

    /**
     * page 있는지 여부 확인
     * 있으면 -> 사용 표시 + pin
     * 없으면 -> 빈 프레임 확보
     *   -> 기존 페이지가 Dirty이면 disk write
     *   -> 메타데이터 초기화 및 매핑업데이트
     *   -> disk manager 데이터 읽어오기
     *   -> pin
     *   -> 새로운 매핑 등록
     * freeFrameId의 경우에는 LRU 알고리즘 사용
     * */
    fun fetchPage(pageId: Long): PageHandle{
        var frame: Frame?
        var victimPageId: Long? = null
        var needIO = false

        globalLatch.lock()
        try{
            if(pageTable.containsKey(pageId)) {
                val frameId = pageTable[pageId]!!
                frame = frames[frameId]
                replacer.pin(frameId)
            } else {
                val freeFrameId = getFreeFrameId()
                frame = frames[freeFrameId]
                if(frame.isDirty && frame.pageId != null){
                    victimPageId = frame.pageId
                }
                pageTable.remove(frame.pageId)
                frame.pageId = pageId
                frame.pinCount = 1
                replacer.pin(freeFrameId)
                pageTable[pageId] = freeFrameId
                needIO = true
                frame.latch.writeLock().lock()
            }
        } finally {
            globalLatch.unlock()
        }
        if(needIO){
            try{
                if(victimPageId != null){
                    diskManager.writePage(victimPageId, frame.data)
                }
                frame.reset()
                diskManager.readPage(pageId, frame.data)
            } finally {
                frame.latch.writeLock().unlock()
            }
        } else {
            frame.latch.readLock().lock()
            frame.latch.readLock().unlock()
        }
        return PageHandle(frame, this)
    }

    fun newPage(){

    }

    /**
     * page 사용 종료시 handle에서 호출
     * frame 가져와서 -> pinCount -- -> isDirty 몇 표기
     *
     * */
    fun unpinPage(){

    }

    fun flushPage(){

    }

    private fun getFreeFrameId() = if(freeList.isEmpty()) replacer.evict() else freeList.removeFirst()

}