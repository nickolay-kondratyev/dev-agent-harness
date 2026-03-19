package com.glassthought.shepherd.core.executor

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.state.PartResult

/**
 * Executes a single plan part (one or two sub-parts) to completion.
 *
 * A [PartExecutor] is created per part, runs to completion (or failure), and is discarded.
 * It is **not** reused across parts.
 *
 * The single implementation [PartExecutorImpl] handles both doer-only and doer+reviewer
 * parts through an optional reviewer config.
 *
 * See spec: ref.ap.fFr7GUmCYQEV5SJi8p6AS.E (PartExecutor.md)
 */
@AnchorPoint("ap.2vKbN8rMwXpL5jZqYc7Td.E")
fun interface PartExecutor {
    suspend fun execute(): PartResult
}
