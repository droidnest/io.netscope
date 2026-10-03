package io.netscope.ui.viewmodel

import io.netscope.model.NetTransaction

/** What the detail screen shows for the selected transaction id. */
sealed interface DetailUiState {
    /** First load for this id has not completed yet. */
    data object Loading : DetailUiState

    /** The row does not exist (pruned by maxTransactions, cleared, or never existed). */
    data object Missing : DetailUiState

    /** Full transaction, bodies included; re-emitted as the response and timing land. */
    data class Loaded(val transaction: NetTransaction) : DetailUiState
}
