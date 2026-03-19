package com.glassthought.shepherd.usecase.rejectionnegotiation

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.feedback.FeedbackResolution
import com.glassthought.shepherd.feedback.FeedbackResolutionParser
import com.glassthought.shepherd.feedback.ParseResult
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwait
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructOutcome
import java.nio.file.Path

/**
 * Outcome of a per-item rejection negotiation between doer and reviewer.
 *
 * See spec: ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E (REJECTION_NEGOTIATION section in granular-feedback-loop.md)
 */
sealed class RejectionResult {
    /** Reviewer accepted the doer's rejection — item moves to rejected/. */
    object Accepted : RejectionResult() {
        override fun toString(): String = "RejectionResult.Accepted"
    }

    /** Reviewer insisted, doer complied and addressed the item. */
    object AddressedAfterInsistence : RejectionResult() {
        override fun toString(): String = "RejectionResult.AddressedAfterInsistence"
    }

    /** Agent crashed or doer defied authority after reviewer insistence. */
    data class AgentCrashed(val details: String) : RejectionResult()

    /** Agent signaled fail-workflow during negotiation. */
    data class FailedWorkflow(val reason: String) : RejectionResult()
}

/**
 * Per-item rejection negotiation between doer and reviewer.
 *
 * When a doer writes `## Resolution: REJECTED` on a feedback item, this use case
 * orchestrates a single round of negotiation:
 * 1. Reviewer judges the rejection (accept or insist).
 * 2. If reviewer insists, doer must comply.
 *
 * See spec: ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E
 */
fun interface RejectionNegotiationUseCase {
    /**
     * Executes rejection negotiation for a single feedback item.
     *
     * @param doerHandle Handle to the doer agent session.
     * @param reviewerHandle Handle to the reviewer agent session.
     * @param feedbackFilePath Path to the feedback file containing the doer's rejection reasoning.
     * @return [RejectionResult] indicating the outcome of the negotiation.
     */
    suspend fun execute(
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle,
        feedbackFilePath: Path,
    ): RejectionResult
}

/**
 * Default implementation of [RejectionNegotiationUseCase].
 *
 * Owns its own message templates (inline, NOT via ContextForAgentProvider).
 * Uses [ReInstructAndAwait] for communication with both agents.
 *
 * Flow:
 * 1. Read feedback file to get doer's rejection reasoning.
 * 2. Send rejection + reasoning to reviewer asking for judgment.
 * 3. If reviewer signals PASS → [RejectionResult.Accepted].
 * 4. If reviewer signals NEEDS_ITERATION → re-instruct doer to comply.
 * 5. Read feedback file again; ADDRESSED → [RejectionResult.AddressedAfterInsistence],
 *    still REJECTED → [RejectionResult.AgentCrashed] (doer defied authority).
 *
 * See spec: ref.ap.fvpIuw4Yeeq1IXDvLC3mL.E
 */
// ap.cGkhniuHpDBfYmBQH36ea.E — RejectionNegotiationUseCaseImpl
@AnchorPoint("ap.cGkhniuHpDBfYmBQH36ea.E")
class RejectionNegotiationUseCaseImpl(
    private val reInstructAndAwait: ReInstructAndAwait,
    private val feedbackFileReader: FeedbackFileReader,
    outFactory: OutFactory,
) : RejectionNegotiationUseCase {

    private val out = outFactory.getOutForClass(RejectionNegotiationUseCaseImpl::class)

    override suspend fun execute(
        doerHandle: SpawnedAgentHandle,
        reviewerHandle: SpawnedAgentHandle,
        feedbackFilePath: Path,
    ): RejectionResult {
        // Step 1: Read the feedback file to get doer's rejection reasoning.
        val feedbackContent = feedbackFileReader.readContent(feedbackFilePath)

        out.info("rejection_negotiation_started")

        // Step 2: Send rejection + reasoning to reviewer for judgment.
        val reviewerMessage = buildReviewerJudgmentMessage(feedbackContent, feedbackFilePath)
        val reviewerOutcome = reInstructAndAwait.execute(reviewerHandle, reviewerMessage)

        return when (reviewerOutcome) {
            is ReInstructOutcome.Crashed ->
                RejectionResult.AgentCrashed(reviewerOutcome.details)

            is ReInstructOutcome.FailedWorkflow ->
                RejectionResult.FailedWorkflow(reviewerOutcome.reason)

            is ReInstructOutcome.Responded ->
                handleReviewerResponse(reviewerOutcome, doerHandle, feedbackFilePath)
        }
    }

    private suspend fun handleReviewerResponse(
        reviewerOutcome: ReInstructOutcome.Responded,
        doerHandle: SpawnedAgentHandle,
        feedbackFilePath: Path,
    ): RejectionResult {
        return when (reviewerOutcome.signal.result) {
            DoneResult.PASS -> {
                out.info("reviewer_accepted_rejection")
                RejectionResult.Accepted
            }

            DoneResult.NEEDS_ITERATION -> {
                out.info("reviewer_insists_on_item")
                handleReviewerInsistence(doerHandle, feedbackFilePath)
            }

            DoneResult.COMPLETED -> {
                // COMPLETED is not an expected signal from a reviewer during negotiation.
                RejectionResult.AgentCrashed(
                    "Reviewer sent unexpected COMPLETED signal during rejection negotiation — " +
                        "expected PASS or NEEDS_ITERATION"
                )
            }
        }
    }

    private suspend fun handleReviewerInsistence(
        doerHandle: SpawnedAgentHandle,
        feedbackFilePath: Path,
    ): RejectionResult {
        // Step 5: Re-instruct doer to comply with reviewer's insistence.
        val doerMessage = buildDoerComplianceMessage(feedbackFilePath)
        val doerOutcome = reInstructAndAwait.execute(doerHandle, doerMessage)

        return when (doerOutcome) {
            is ReInstructOutcome.Crashed ->
                RejectionResult.AgentCrashed(doerOutcome.details)

            is ReInstructOutcome.FailedWorkflow ->
                RejectionResult.FailedWorkflow(doerOutcome.reason)

            is ReInstructOutcome.Responded -> {
                // Step 6: Read feedback file again for doer's updated resolution.
                val updatedContent = feedbackFileReader.readContent(feedbackFilePath)
                val parseResult = FeedbackResolutionParser.parse(updatedContent)

                when (parseResult) {
                    is ParseResult.Found -> when (parseResult.resolution) {
                        FeedbackResolution.ADDRESSED -> {
                            out.info("doer_addressed_after_insistence")
                            RejectionResult.AddressedAfterInsistence
                        }

                        FeedbackResolution.REJECTED -> {
                            out.info("doer_defied_reviewer_authority")
                            RejectionResult.AgentCrashed(
                                "Doer still REJECTED after reviewer insistence — doer defied authority"
                            )
                        }

                        FeedbackResolution.SKIPPED -> {
                            RejectionResult.AgentCrashed(
                                "Doer wrote SKIPPED after reviewer insistence — expected ADDRESSED"
                            )
                        }
                    }

                    is ParseResult.MissingMarker ->
                        RejectionResult.AgentCrashed(
                            "Doer failed to write resolution marker after compliance instruction"
                        )

                    is ParseResult.InvalidMarker ->
                        RejectionResult.AgentCrashed(
                            "Doer wrote invalid resolution marker after compliance instruction: " +
                                "${parseResult.rawValue}"
                        )
                }
            }
        }
    }

    companion object {
        /**
         * Builds the message sent to the reviewer asking them to judge the doer's rejection.
         *
         * WHY inline templates: RejectionNegotiationUseCase owns its own message templates
         * per spec — not routed through ContextForAgentProvider.
         */
        internal fun buildReviewerJudgmentMessage(
            feedbackContent: String,
            feedbackFilePath: Path,
        ): String = buildString {
            appendLine("The implementor rejected this feedback item.")
            appendLine("Feedback file: $feedbackFilePath")
            appendLine()
            appendLine("--- Feedback file content with rejection reasoning ---")
            appendLine(feedbackContent)
            appendLine("--- End of feedback file content ---")
            appendLine()
            appendLine("Review the reasoning above and decide:")
            appendLine("- If the rejection is valid, signal `done pass`")
            appendLine("- If the item must be addressed, signal `done needs_iteration` with counter-reasoning")
        }

        /**
         * Builds the message sent to the doer when reviewer insists on addressing the item.
         *
         * WHY-NOT: The spec (granular-feedback-loop.md lines 346-348) calls for including the
         * reviewer's counter-reasoning in this message. However, [ReInstructOutcome.Responded]
         * only carries [AgentSignal.Done] with a [DoneResult] enum — no message content.
         * The protocol does not currently support passing textual reasoning back through signals.
         * Including counter-reasoning would require a protocol extension (V2 consideration).
         * The generic authority message is pragmatic for V1.
         */
        internal fun buildDoerComplianceMessage(
            feedbackFilePath: Path,
        ): String = buildString {
            appendLine("Reviewer insists this feedback item must be addressed.")
            appendLine("Feedback file: $feedbackFilePath")
            appendLine()
            appendLine("You MUST address this item. Update the feedback file and write '## Resolution: ADDRESSED'.")
            appendLine("This is non-negotiable — the reviewer is the authority on this item.")
        }
    }
}
