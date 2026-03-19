package com.glassthought.shepherd.core.executor

import com.glassthought.shepherd.core.state.Part

/**
 * Creates a [PartExecutor] for a given [Part].
 *
 * Decouples [TicketShepherd][com.glassthought.shepherd.core.TicketShepherd] from the
 * construction details of [PartExecutorImpl] — the shepherd calls [create] and receives
 * a ready-to-execute [PartExecutor] without knowing about [SubPartConfig], [PartExecutorDeps],
 * or [IterationConfig][com.glassthought.shepherd.core.state.IterationConfig] wiring.
 *
 * Test code substitutes a fake factory that returns controlled [PartExecutor] instances.
 */
fun interface PartExecutorFactory {
    fun create(part: Part): PartExecutor
}
