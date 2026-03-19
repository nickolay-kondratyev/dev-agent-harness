package com.glassthought.shepherd.usecase.healthmonitoring

import com.glassthought.shepherd.core.state.PartResult

/**
 * Records structured failure context for the benefit of the next retry attempt.
 *
 * See ref.ap.cI3odkAZACqDst82HtxKa.E for the full spec.
 * V1: no-op stub ([NoOpTicketFailureLearningUseCase]). Real implementation deferred.
 */
fun interface TicketFailureLearningUseCase {
    suspend fun recordFailureLearning(partResult: PartResult)
}

/** V1 stub — does nothing. Real implementation tracked separately. */
class NoOpTicketFailureLearningUseCase : TicketFailureLearningUseCase {
    override suspend fun recordFailureLearning(partResult: PartResult) {
        // No-op: real implementation deferred to a future ticket.
    }
}
