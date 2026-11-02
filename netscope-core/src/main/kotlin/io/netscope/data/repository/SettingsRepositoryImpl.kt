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
) : SettingsRepository {

    override fun getSettings(): Settings {
        val config = NetScope.config
        return Settings(
            maxTransactions        = config.maxTransactions,
            maxBodySize            = config.maxBodySize,
            alwaysReadResponseBody = config.alwaysReadResponseBody,
            redactedHeaders        = config.redactedHeaders,
            shakeToOpen            = NetScope.shakeEnabled,
            notificationStyle      = NetScope.notificationStyle,
        )
    }

    override fun update(change: SettingChange) {
        when (change) {
            is SettingChange.MaxTransactions ->
                NetScope.updateConfig { copy(maxTransactions = change.count) }
            is SettingChange.MaxBodySize ->
                NetScope.updateConfig { copy(maxBodySize = change.bytes) }
            is SettingChange.AlwaysReadResponseBody ->
                NetScope.updateConfig { copy(alwaysReadResponseBody = change.isEnabled) }
            is SettingChange.RedactedHeaders ->
                NetScope.updateConfig { copy(redactedHeaders = change.headers.map { it.lowercase() }.toSet()) }
            is SettingChange.ShakeToOpen ->
                NetScope.setShakeEnabled(enabled = change.isEnabled)
            is SettingChange.Notifications ->
                NetScope.notificationStyle = change.style
        }
        savedSettings.save(change = change)
    }
}
