package config

/** Root config for one [database.DataBase] instance. */
data class SimpleConfig(
    val storageConfig: StorageConfig = StorageConfig(),
    val indexConfig: IndexConfig = IndexConfig()
)

/**
 * @property dbPath Path to the database file (opened/created by [storageEngine.DiskManager]).
 * @property poolSize Number of frames in the buffer pool (see [storageEngine.BufferPoolManager]).
 * */
data class StorageConfig (
    val dbPath: String = "js.db",
    val poolSize: Int = 2000,
    val midPointLruConfig: MidpointLruConfig = MidpointLruConfig()
)

/** Tuning for [storageEngine.lru.GenerationalList]'s Midpoint LRU — see its class doc for what each of these means. */
data class MidpointLruConfig (
    val capacity: Int = 2000,
    val lruOldBlocksTimeMs: Long = 1000,
    val youngRatio: Double = 0.63,
    val lruOldMinLength: Int = 50
)

/**
 * @property pageSize Fixed page size in bytes (must match [storageEngine.page.Page.HEADER_SIZE] + room for at least a few slots/records).
 * @property maxKeys Split/merge threshold for [index.btree.node.Node] (`keyCount`-based; see [index.btree.node.Node.isOverflow]/`isUnderflow`).
 * */
data class IndexConfig (
    val pageSize: Int = 4096,
    val maxKeys: Int = 64,
)
