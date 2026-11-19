package io.netscope.ui.detail

import io.netscope.model.NetTransaction

/** Content of the detail screen. */
internal data class TransactionDetailUiState(
    val transaction: NetTransaction,
    val selectedTab: Int = 0,
    val isMenuOpen: Boolean = false,
    /** Whether the search bar of the Request and Response tabs shows. */
    val isSearchOpen: Boolean = false,
    /** What the Request and Response tabs mark in their headers and body. */
    val searchQuery: String = "",
)
