package io.netscope.domain.repository

import io.netscope.model.SettingChange
import io.netscope.model.Settings

/** The settings the library runs with, and the ones the user saved in the Settings screen. */
interface SettingsRepository {

    /** Returns what the library currently runs with. */
    fun getSettings(): Settings

    /**
     * Applies [change] to the running library at once and saves it for the next launches.
     * Headers in [Settings.lockedRedactedHeaders] stay redacted whatever [change] says.
     */
    fun update(change: SettingChange)
}
