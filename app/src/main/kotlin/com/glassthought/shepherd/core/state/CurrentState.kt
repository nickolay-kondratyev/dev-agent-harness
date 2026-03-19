package com.glassthought.shepherd.core.state

/**
 * In-memory representation of the current execution state.
 *
 * Wraps [Part] list as a mutable structure so the executor can update status,
 * iteration counts, and session records during execution.
 *
 * @property parts Mutable list of plan parts with their current runtime state.
 */
data class CurrentState(
    val parts: MutableList<Part>,
)
