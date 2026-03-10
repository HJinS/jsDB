package storageEngine

import config.IndexConfig
import index.btree.node.InternalNode
import index.serializer.MultiColumnKeySerializer
import storageEngine.DiskManager
import storageEngine.lru.MidpointLRUPolicy
import storageEngine.page.Frame
import storageEngine.page.Page
import storageEngine.page.PageHandle
import storageEngine.page.SlottedPage
import storageEngine.util.PageType
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
    indexConfig: IndexConfig,
    poolSize: Int
){
    private val frames: Array<Frame> = Array(poolSize) { Frame(it, -1, indexConfig.pageSize) }
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
                if(frame.isDirty && frame.pageId != -1L){
                    victimPageId = frame.pageId
                }
                pageTable.remove(frame.pageId)
                frame.pageId = pageId
                frame.pinCount = 1
                replacer.pin(freeFrameId)
                pageTable[pageId] = freeFrameId
                needIO = true
                // writeLock을 여기서 미리 잡아둠. 밑에서 IO 작업 진행 필요
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
            // Case A (이미 존재)의 경우:
            // 만약 I/O 중인 프레임을 만났다면 데이터가 유효해질 때까지 기다려야 함
            // ReadLock을 잠깐 잡았다 놓음으로써 WriteLock(I/O)이 끝날 때까지 대기 효과
            frame.latch.readLock().lock()
            frame.latch.readLock().unlock()
        }
        return PageHandle(frame, this)
    }

    /**
     * 1. 새로운 PageID 할당(pageID 관리는 보통 disk manager 가 관리함)
     * 2. 빈 Frame 탐색 -> 빈 프레임이 없으면 eviction 실행 후 공간 확보(disk flush 필요.)
     * 3. pageTable 업데이트, page pin, dirty 마킹, 페이지 초기화(헤더 등)
     * */
    fun newPage(): PageHandle{
        val newPageId = diskManager.allocatePage()
        val frame = frames[getFreeFrameId()]
        frame.reset()
        frame.isDirty = true
        pageTable[newPageId] = frame.frameId
        frame.pageId = newPageId
        replacer.pin(frame.frameId)
        return PageHandle(frame, this)
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