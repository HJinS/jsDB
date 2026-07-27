package config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource

@Suppress("unused")
object ConfigLoader {
    fun load() = ConfigLoaderBuilder.default()
        .addResourceSource("/config.yml")
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow<SimpleConfig>()
}