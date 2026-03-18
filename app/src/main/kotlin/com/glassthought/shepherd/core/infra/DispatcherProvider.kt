package com.glassthought.shepherd.core.infra

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides coroutine dispatchers, enabling testability by allowing dispatcher substitution.
 *
 * Inject instead of referencing `Dispatchers.IO` directly — satisfies Sonar S6310 and
 * keeps coroutine scheduling under test control.
 *
 * ap.PCmO6HVD8MShEJOnyyVih.E
 */
fun interface DispatcherProvider {
    fun io(): CoroutineDispatcher

    companion object {
        /** Returns a provider backed by the standard [Dispatchers.IO]. */
        fun standard(): DispatcherProvider = DispatcherProvider { Dispatchers.IO }
    }
}
