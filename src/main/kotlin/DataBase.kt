import config.SimpleConfig
import index.btree.BTree
import index.serializer.KeySerializer
import index.serializer.ValueSerializer
import storageEngine.BufferPoolManager
import storageEngine.DatabaseInitializer
import storageEngine.DiskManager
import storageEngine.FreeSpaceManager
import storageEngine.StorageManager
import storageEngine.lru.MidpointLRUPolicy


class DataBase(private val config: SimpleConfig) {
    private val diskManager = DiskManager(config.storageConfig, config.indexConfig)
    private val lruPolicy = MidpointLRUPolicy(config.storageConfig.midPointLruConfig)
    private val bufferPoolManager = BufferPoolManager(diskManager, lruPolicy, config.indexConfig, config.storageConfig.poolSize)
    private val databaseInitializer = DatabaseInitializer(bufferPoolManager)
    private val freeSpaceManager = FreeSpaceManager(bufferPoolManager)
    private val storageManager = StorageManager(freeSpaceManager, bufferPoolManager, config.indexConfig)

    fun initialize() = databaseInitializer.initMetaPage()

    fun <K, V> createIndex(
        name: String,
        targetTable: String,
        keySerializer: KeySerializer<K>,
        valueSerializer: ValueSerializer<V>,
    ): BTree<K, V> {
        return BTree(name, targetTable, storageManager, keySerializer, valueSerializer, config.indexConfig)
    }
}