package com.glassthought.shepherd.usecase.finalcommit

/**
 * Performs a final `git add -A && git commit` to capture any remaining state
 * (e.g., final `CurrentState` flush to `current_state.json`).
 *
 * Skipped if the working tree is clean (no changes since last commit).
 */
fun interface FinalCommitUseCase {
    suspend fun commitIfDirty()
}
