package io.netscope.domain.usecase.settings

import io.netscope.domain.repository.SettingsRepository
import io.netscope.model.Settings

/** Reads the settings the library runs with. */
class GetSettingsUseCase(
    private val repository: SettingsRepository,
) {

    /** Returns what the library currently runs with. */
    operator fun invoke(): Settings = repository.getSettings()
}
