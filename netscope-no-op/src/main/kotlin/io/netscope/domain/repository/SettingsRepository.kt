package io.netscope.domain.repository

import io.netscope.model.SettingChange
import io.netscope.model.Settings

/** No-op stub matching the real [SettingsRepository] in netscope-core. */
interface SettingsRepository {
    fun getSettings(): Settings
    fun update(change: SettingChange)
}
