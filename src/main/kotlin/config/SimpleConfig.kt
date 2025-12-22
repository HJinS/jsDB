package config

import kotlinx.serialization.Serializable

@Serializable
data class SimpleConfig(
    val midpointLruConfig: StorageConfig = StorageConfig()
)

@Serializable
data class StorageConfig(
    val dbPath: String = "var/lib/jsdb",
    val midPointLruConfig: MidpointLruConfig = MidpointLruConfig(2000, 1000),
    val pageConfig: PageConfig = PageConfig()
)

@Serializable
data class MidpointLruConfig(
    val capacity: Int,
    val lruOldBlocksTimeMs: Long = 1000,
    val youngRatio: Double = 0.63
)

@Serializable
data class PageConfig(
    val pageSize: Int = 4096
)
