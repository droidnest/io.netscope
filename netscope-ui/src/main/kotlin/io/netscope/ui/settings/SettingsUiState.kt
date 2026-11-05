package io.netscope.ui.settings

import io.netscope.model.Settings

/** State of the Settings screen. */
internal sealed interface SettingsUiState {

    val content: SettingsContentUi

    data class Content(override val content: SettingsContentUi) : SettingsUiState

    /** Every transaction was cleared: the screen leaves. */
    data class Navigating(override val content: SettingsContentUi) : SettingsUiState
}

internal data class SettingsContentUi(
    val settings: Settings,
    val isClearDialogOpen: Boolean = false,
    val isAddHeaderOpen: Boolean = false,
    val addHeaderInput: String = "",
)
