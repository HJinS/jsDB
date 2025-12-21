package config

import kotlinx.serialization.Serializable

@Serializable
data class SimpleConfig(
    val midpointLruConfig: MidpointLruConfig = MidpointLruConfig(2000, 1000)
)


@Serializable
data class MidpointLruConfig(
    val capacity: Int,
    val lruOldBlocksTimeMs: Long = 1000,
    val youngRatio: Double = 0.63
)