package config



data class SimpleConfig(
    val midpointLruConfig: StorageConfig = StorageConfig(),
    val indexConfig: IndexConfig = IndexConfig()
)


data class StorageConfig (
    val dbPath: String = "js.db",
    val midPointLruConfig: MidpointLruConfig = MidpointLruConfig()
)


data class MidpointLruConfig (
    val capacity: Int = 2000,
    val lruOldBlocksTimeMs: Long = 1000,
    val youngRatio: Double = 0.63
)


data class IndexConfig (
    val pageSize: Int = 4096,
    val maxKeys: Int = 64,
    val allowDuplicate: Boolean = true
)
