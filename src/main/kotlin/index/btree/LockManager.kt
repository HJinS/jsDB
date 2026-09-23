package index.btree

import util.LockMode
import storageEngine.page.PageLock


/**
 * Tracks the [PageLock]s acquired while descending a [BTree] (latch crabbing), in acquisition
 * order — root first, most recently visited node last. One instance is created per operation
 * (insert/delete/update/search/scan) and threaded through the descent; see
 * `docs/index/btree-latch-crabbing.md` for the overall scheme.
 *
 * @property lockMode The [LockMode] this descent acquires pages with — fixed for the whole walk
 *   (READ for search/scan, WRITE for insert/delete/update).
 * */
class LockManager(val lockMode: LockMode): AutoCloseable{
    private val lockQueue = ArrayDeque<PageLock>()

    /** Number of locks currently held. */
    val size: Int
        get() = lockQueue.size

    /** The most recently pushed lock — the deepest node reached so far. */
    val last: PageLock
        get() = lockQueue.last()

    /** The lock at [idx], in acquisition order (index 0 = root). */
    fun at(idx: Int) = lockQueue[idx]

    /** Records a newly acquired lock as the new deepest one. */
    fun push(lock: PageLock){
        lockQueue.add(lock)
    }

    /**
     * Releases every ancestor lock strictly above [lock] — everything from the front of the queue
     * up to (but not including) [lock] — in FIFO order (root first), then returns [lock] (now the
     * front of the queue, since everything before it is gone).
     *
     * Called once a node is known to be "safe" ([index.btree.node.Node.isSafeNode]): if this
     * node's operation can't propagate a split/merge upward, none of the ancestor locks are
     * needed anymore.
     * */
    fun releaseAncestor(lock: PageLock): PageLock{
        while(lockQueue.isNotEmpty() && lockQueue.first() !== lock) lockQueue.removeFirst().close()
        return lockQueue.first()
    }

    /** Closes (unlock + unpin) and removes one specific [lock], wherever it sits in the queue. */
    fun closeAndRemoveLock(lock: PageLock){
        lock.close()
        lockQueue.remove(lock)
    }

    /** Releases every remaining lock, in FIFO order. Safe to call on an already-empty queue. */
    override fun close(){
         while(lockQueue.isNotEmpty()) lockQueue.removeFirst().close()
    }
}
