package com.glassthought.shepherd.core.state

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.state.SubPartStatus.COMPLETED
import com.glassthought.shepherd.core.state.SubPartStatus.FAILED
import com.glassthought.shepherd.core.state.SubPartStatus.IN_PROGRESS
import com.glassthought.shepherd.core.state.SubPartStatus.NOT_STARTED

/**
 * Sealed class encoding every legal [SubPartStatus] transition. The KDoc on each entry **is** the
 * state machine diagram — one authoritative place to audit the state machine.
 *
 * [PartExecutorImpl] calls the validator before every status mutation: no status field update
 * without a validated transition. Invalid (status, trigger) combinations throw immediately at the
 * transition site — silent acceptance of invalid transitions is impossible.
 *
 * @see SubPartStatus.transitionTo
 * @see SubPartStatus.validateCanSpawn
 */
@AnchorPoint("ap.EHY557yZ39aJ0lV00gPGF.E")
sealed class SubPartStateTransition {

    /**
     * NOT_STARTED → IN_PROGRESS
     *
     * Trigger: harness spawns the agent for this sub-part (no AgentSignal involved).
     * Validated by: [SubPartStatus.validateCanSpawn] — throws if status != NOT_STARTED.
     */
    object Spawn : SubPartStateTransition()

    /**
     * IN_PROGRESS → COMPLETED
     *
     * Triggers:
     *   - doer (doer-only part):    [AgentSignal.Done]([DoneResult.COMPLETED])
     *   - reviewer:                 [AgentSignal.Done]([DoneResult.PASS])
     *   - doer (doer+reviewer part): applied by executor simultaneously with reviewer PASS —
     *     the doer does NOT signal COMPLETED per iteration; it stays IN_PROGRESS until the
     *     part completes, then the executor marks both reviewer and doer COMPLETED.
     */
    object Complete : SubPartStateTransition()

    /**
     * IN_PROGRESS → FAILED
     *
     * Triggers:
     *   - [AgentSignal.FailWorkflow]
     *   - [AgentSignal.Crashed]
     */
    object Fail : SubPartStateTransition()

    /**
     * IN_PROGRESS → IN_PROGRESS  (reviewer sub-part only; status value unchanged)
     *
     * Trigger: reviewer [AgentSignal.Done]([DoneResult.NEEDS_ITERATION]).
     * Side-effect handled by executor (not the validator): iteration.current is incremented.
     */
    object IterateContinue : SubPartStateTransition()
}

/**
 * Maps an [AgentSignal] to the corresponding [SubPartStateTransition] for this status.
 * Covers transitions: [SubPartStateTransition.Complete], [SubPartStateTransition.Fail],
 * [SubPartStateTransition.IterateContinue].
 *
 * @throws IllegalStateException if the (status, signal) pair is not a valid transition.
 *
 * Note: [AgentSignal.SelfCompacted] does NOT trigger a SubPart status change — it is handled
 *       inside the facade's health-aware await loop and is invisible to PartExecutorImpl.
 * Note: In doer+reviewer parts, the executor does NOT call this for the doer's Done(COMPLETED)
 *       signal — the doer stays IN_PROGRESS; the executor just proceeds to instruct the reviewer.
 *       The doer's COMPLETED transition happens separately when reviewer sends PASS.
 */
fun SubPartStatus.transitionTo(signal: AgentSignal): SubPartStateTransition {
    return when (this) {
        IN_PROGRESS -> when (signal) {
            is AgentSignal.Done -> when (signal.result) {
                DoneResult.COMPLETED, DoneResult.PASS -> SubPartStateTransition.Complete
                DoneResult.NEEDS_ITERATION -> SubPartStateTransition.IterateContinue
            }
            is AgentSignal.FailWorkflow -> SubPartStateTransition.Fail
            is AgentSignal.Crashed -> SubPartStateTransition.Fail
            AgentSignal.SelfCompacted ->
                error("SelfCompacted is transparent to SubPart status; handle inside facade, not executor")
        }
        NOT_STARTED ->
            error("Cannot apply AgentSignal to NOT_STARTED; call validateCanSpawn() before spawning")
        COMPLETED ->
            error("COMPLETED is terminal — no further transitions allowed")
        FAILED ->
            error("FAILED is terminal — no further transitions allowed")
    }
}

/**
 * Validates that this sub-part is [NOT_STARTED] before the harness spawns an agent.
 * Returns [SubPartStateTransition.Spawn] on success.
 *
 * @throws IllegalStateException if status != [NOT_STARTED].
 */
fun SubPartStatus.validateCanSpawn(): SubPartStateTransition.Spawn {
    check(this == NOT_STARTED) { "spawn requires NOT_STARTED, got $this" }
    return SubPartStateTransition.Spawn
}
