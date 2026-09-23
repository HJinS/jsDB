package config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource

/**
 * Loads [SimpleConfig] from `resources/config.yml` (falling back to environment variables per
 * Hoplite's precedence rules) instead of using [SimpleConfig]'s in-code defaults.
 *
 * **Not currently wired up anywhere** — no `resources/config.yml` exists yet, and nothing calls
 * [load] (hence the `@Suppress("unused")`). Every current caller (`DataBase`, tests) constructs
 * [SimpleConfig] directly instead.
 * */
@Suppress("unused")
object ConfigLoader {
    fun load() = ConfigLoaderBuilder.default()
        .addResourceSource("/config.yml")
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow<SimpleConfig>()
}