package config



object SimpleConfig{
    val midpointLruConfig: StorageConfig = StorageConfig
    val indexConfig: IndexConfig = IndexConfig
}


object StorageConfig {
    val dbPath: String = "var/lib/jsdb"
    val midPointLruConfig: MidpointLruConfig = MidpointLruConfig
}


object MidpointLruConfig {
    val capacity: Int = 2000
    val lruOldBlocksTimeMs: Long = 1000
    val youngRatio: Double = 0.63
}


object IndexConfig {
    val pageSize: Int = 4096
    val maxKeys: Int = 64
    val allowDuplicate: Boolean = true
}
