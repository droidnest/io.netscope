package io.netscope.ui.settings

import io.netscope.model.SettingChange
import io.netscope.ui.mvi.UiIntent

internal sealed interface SettingsUiIntent : UiIntent {
    data class SettingChanged(val change: SettingChange) : SettingsUiIntent
    data object AddHeaderClicked : SettingsUiIntent
    data class HeaderTyped(val input: String) : SettingsUiIntent
    data object AddHeaderConfirmed : SettingsUiIntent
    data class HeaderRemoved(val header: String) : SettingsUiIntent
    data object ClearAllClicked : SettingsUiIntent
    data object ClearAllCancelled : SettingsUiIntent
    data object ClearAllConfirmed : SettingsUiIntent
    data object NavigationHandled : SettingsUiIntent
}
