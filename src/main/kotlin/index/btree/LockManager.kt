package index.btree

import storageEngine.util.LockMode
import storageEngine.page.PageLock

import index.exception.IllegalLatchStateException


class LockManager(val lockMode: LockMode): AutoCloseable{
    private val lockQueue = ArrayDeque<PageLock>()

    val size: Int
        get() = lockQueue.size

    val last: PageLock
        get() = lockQueue.last()

    fun at(idx: Int) = lockQueue[idx]

    fun lockPush(lock: PageLock){
        when(lockMode){
            LockMode.READ -> lock.lockRead()
            LockMode.WRITE -> lock.lockWrite()
        }
        push(lock)
    }

    fun push(lock: PageLock){
        lockQueue.add(lock)
    }
    
    // 조상 페이지들을 순서대로 unlock & unpin 진행 && 가장 최근 Lock 반환
    fun realeaseAncester(lock: PageLock): PageLock{
        if(lockQueue.lastOrNull() !== lock) throw IllegalLatchStateException.InvalidLockObjectError(lock.frameId)
        while(lockQueue.size > 1) lockQueue.removeFirst().close()
        return lockQueue.last()
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
