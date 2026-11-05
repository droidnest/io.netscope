package io.netscope.ui.mvi

/** What a screen shows. Its [T] is the content, or the flow's screen state. */
internal sealed interface UiState<out T> {

    /** Nothing was asked for yet. */
    data object Idle : UiState<Nothing>

    /** The content is on its way. */
    data object Loading : UiState<Nothing>

    /** The content is ready. */
    data class Success<T>(val data: T) : UiState<T>

    /** The content could not be shown, for the reason in [message]. */
    data class Error(val message: String?) : UiState<Nothing>

    /** Returns the content, or null when there is none. */
    fun dataOrNull(): T? = (this as? Success)?.data
}
