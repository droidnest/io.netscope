package io.netscope.ui.detail

import io.netscope.model.NetTransaction

/** Content of the detail screen. */
internal data class TransactionDetailUiState(
    val transaction: NetTransaction,
    val selectedTab: Int = 0,
    val isMenuOpen: Boolean = false,
)
