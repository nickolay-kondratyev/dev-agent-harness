package com.glassthought.shepherd.usecase.reinstructandawait

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.facade.AgentFacade
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import java.nio.file.Path

/**
 * Outcome of a re-instruction delivery to an existing agent session.
 *
 * Maps the raw [AgentSignal] variants to caller-appropriate outcomes,
 * eliminating duplicated plumbing at each call site.
 *
 * See spec: ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E (ReInstructAndAwait.md)
 */
// ap.QZYYZ2gTi1D2SQ5IYxOU6.E — sealed class defined per spec
sealed class ReInstructOutcome {
    /**
     * Agent responded to the instruction.
     * Caller handles the signal normally.
     */
    data class Responded(val signal: AgentSignal.Done) : ReInstructOutcome()

    /**
     * Agent signaled fail-workflow during the await.
     * Caller propagates as PartResult.FailedWorkflow(reason).
     */
    data class FailedWorkflow(val reason: String) : ReInstructOutcome()

    /**
     * Agent crashed or timed out during the await.
     * Caller propagates as PartResult.AgentCrashed(details).
     */
    data class Crashed(val details: String) : ReInstructOutcome()
}

/**
 * Delivers an instruction to an existing agent session and awaits the next signal,
 * mapping [AgentSignal] to [ReInstructOutcome].
 *
 * Eliminates ~15-20 lines of duplicated plumbing at each call site in the granular
 * feedback loop and rejection negotiation paths.
 *
 * See spec: ref.ap.QZYYZ2gTi1D2SQ5IYxOU6.E (ReInstructAndAwait.md)
 */
interface ReInstructAndAwait {
    /**
     * Delivers an instruction message to an existing agent session and awaits
     * the next signal.
     *
     * Internally delegates to `agentFacade.sendPayloadAndAwaitSignal(handle, message)` --
     * the facade owns fresh deferred creation, SessionEntry re-registration, ACK delivery,
     * and the health-aware await loop (ref.ap.QCjutDexa2UBDaKB3jTcF.E).
     *
     * Maps the raw [AgentSignal] to [ReInstructOutcome] for cleaner call-site `when` branches.
     * Returns [ReInstructOutcome.Responded], [ReInstructOutcome.FailedWorkflow], or [ReInstructOutcome.Crashed].
     *
     * @param handle The spawned agent handle identifying the existing session.
     * @param message Absolute path to the instruction file to deliver to the agent.
     */
    suspend fun execute(
        handle: SpawnedAgentHandle,
        message: String,
    ): ReInstructOutcome
}

/**
 * Default implementation of [ReInstructAndAwait].
 *
 * Bridges the [message] (file path string) to [AgentPayload] and delegates
 * to [AgentFacade.sendPayloadAndAwaitSignal], then maps the returned [AgentSignal]
 * to a [ReInstructOutcome].
 *
 * [AgentSignal.SelfCompacted] should never reach this class -- the facade handles
 * self-compaction transparently (ref.ap.HU6KB4uRDmOObD54gdjYs.E). If it does arrive,
 * it is treated as a crash to surface the unexpected state.
 */
@AnchorPoint("ap.fXi4IJBxh0ez1Z7tvoamj.E")
class ReInstructAndAwaitImpl(
    private val agentFacade: AgentFacade,
) : ReInstructAndAwait {

    override suspend fun execute(
        handle: SpawnedAgentHandle,
        message: String,
    ): ReInstructOutcome {
        val payload = AgentPayload(instructionFilePath = Path.of(message))
        val signal = agentFacade.sendPayloadAndAwaitSignal(handle, payload)

        return when (signal) {
            is AgentSignal.Done -> ReInstructOutcome.Responded(signal)
            is AgentSignal.FailWorkflow -> ReInstructOutcome.FailedWorkflow(signal.reason)
            is AgentSignal.Crashed -> ReInstructOutcome.Crashed(signal.details)
            is AgentSignal.SelfCompacted -> ReInstructOutcome.Crashed(
                "Unexpected SelfCompacted signal reached ReInstructAndAwait — " +
                    "facade should handle self-compaction transparently"
            )
        }
    }
}
