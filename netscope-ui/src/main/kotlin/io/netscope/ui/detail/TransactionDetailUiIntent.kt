package io.netscope.ui.detail

import io.netscope.ui.mvi.UiIntent

internal sealed interface TransactionDetailUiIntent : UiIntent {
    data class Started(val transactionId: Long) : TransactionDetailUiIntent
    data class TabClicked(val tab: Int) : TransactionDetailUiIntent
    data object MenuClicked : TransactionDetailUiIntent
    data object MenuDismissed : TransactionDetailUiIntent
    data object ErrorShown : TransactionDetailUiIntent
}
