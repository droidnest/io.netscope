package io.netscope.domain.usecase.settings

import io.netscope.domain.repository.SettingsRepository
import io.netscope.model.SettingChange

/** Applies a setting the user changed. */
class UpdateSettingUseCase(
    private val repository: SettingsRepository,
) {

    /** Applies [change] to the running library at once and saves it for the next launches. */
    operator fun invoke(change: SettingChange) = repository.update(change = change)
}
