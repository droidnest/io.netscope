package io.netscope.ui.mvi

import androidx.lifecycle.ViewModel

/** Base of the ViewModels: a screen talks to one only through [onIntent]. */
internal abstract class BaseViewModel : ViewModel() {

    /** Reacts to [intent]; the ones of another flow are ignored. */
    abstract fun onIntent(intent: UiIntent)
}
