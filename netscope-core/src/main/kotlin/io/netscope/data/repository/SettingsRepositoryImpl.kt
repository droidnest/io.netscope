package io.netscope.data.repository

import io.netscope.NetScope
import io.netscope.data.local.SavedSettings
import io.netscope.domain.repository.SettingsRepository
import io.netscope.model.SettingChange
import io.netscope.model.Settings

/**
 * Reads the settings from the running library (the app's configuration plus what the user saved)
 * and applies each change to it before saving it in [savedSettings].
 */
internal class SettingsRepositoryImpl(
    private val savedSettings: SavedSettings,
    private val configuredRedactedHeaders: Set<String>,
) : SettingsRepository {

    override fun getSettings(): Settings {
        val config = NetScope.config
        return Settings(
            maxTransactions        = config.maxTransactions,
            maxBodySize            = config.maxBodySize,
            alwaysReadResponseBody = config.alwaysReadResponseBody,
            redactedHeaders        = config.redactedHeaders,
            lockedRedactedHeaders  = configuredRedactedHeaders,
            shakeToOpen            = NetScope.shakeEnabled,
            notificationStyle      = NetScope.notificationStyle,
        )
    }

    override fun update(change: SettingChange) {
        val applied = change.keepingRedactionsOf(configuredHeaders = configuredRedactedHeaders)
        when (applied) {
            is SettingChange.MaxTransactions ->
                NetScope.updateConfig { copy(maxTransactions = applied.count) }
            is SettingChange.MaxBodySize ->
                NetScope.updateConfig { copy(maxBodySize = applied.bytes) }
            is SettingChange.AlwaysReadResponseBody ->
                NetScope.updateConfig { copy(alwaysReadResponseBody = applied.isEnabled) }
            is SettingChange.RedactedHeaders ->
                NetScope.updateConfig { copy(redactedHeaders = applied.headers) }
            is SettingChange.ShakeToOpen ->
                NetScope.setShakeEnabled(enabled = applied.isEnabled)
            is SettingChange.Notifications ->
                NetScope.notificationStyle = applied.style
        }
        savedSettings.save(change = applied)
    }
}

/** This change, with [configuredHeaders] added back: Settings never un-redacts what the app redacts. */
internal fun SettingChange.keepingRedactionsOf(configuredHeaders: Set<String>): SettingChange =
    when (this) {
        is SettingChange.RedactedHeaders ->
            copy(headers = headers.map { it.lowercase() }.toSet() + configuredHeaders)
        else -> this
    }
