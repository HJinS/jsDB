package index.btree

import storageEngine.util.LockMode
import storageEngine.page.PageLock



class LockManager(val lockMode: LockMode): AutoCloseable{
    private val lockQueue = ArrayDeque<PageLock>()

    val size: Int
        get() = lockQueue.size

    val last: PageLock
        get() = lockQueue.last()

    fun at(idx: Int) = lockQueue[idx]

    fun push(lock: PageLock){
        lockQueue.add(lock)
    }
    
    // 조상 페이지들을 순서대로 unlock & unpin 진행 && 가장 최근 Lock 반환
    fun releaseAncestor(lock: PageLock): PageLock{
        while(lockQueue.isNotEmpty() && lockQueue.first() !== lock) lockQueue.removeFirst().close()
        return lockQueue.first()
    }

    fun closeAndRemoveLock(lock: PageLock){
        lock.close()
        lockQueue.remove(lock)
    }
    
    // 모든 페이지의 lock을 해제하고 unpin 함
    override fun close(){
         while(lockQueue.isNotEmpty()) lockQueue.removeFirst().close()
    }
}
