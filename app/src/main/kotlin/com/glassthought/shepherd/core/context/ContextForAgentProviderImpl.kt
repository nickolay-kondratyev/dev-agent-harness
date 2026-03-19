package com.glassthought.shepherd.core.context

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import java.nio.file.Path

/**
 * Default implementation of [ContextForAgentProvider].
 *
 * `assembleInstructions` dispatches on the sealed [AgentInstructionRequest] type to pick the
 * correct instruction plan. Each private `build*Plan()` method returns a `List<InstructionSection>`
 * that IS the documentation of the concatenation order. The [assembler] renders and writes the plan.
 *
 * See ContextForAgentProvider.md (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E) for the authoritative
 * concatenation tables.
 */
class ContextForAgentProviderImpl(
    outFactory: OutFactory,
    private val assembler: InstructionPlanAssembler,
) : ContextForAgentProvider {

    private val out = outFactory.getOutForClass(ContextForAgentProviderImpl::class)

    override suspend fun assembleInstructions(
        request: AgentInstructionRequest,
    ): Path {
        out.debug("assembling_instructions") {
            buildList {
                add(Val(request::class.simpleName ?: "Unknown", ValType.STRING_USER_AGNOSTIC))
                add(Val(request.iterationNumber.toString(), ValType.STRING_USER_AGNOSTIC))
                request.executionContextOrNull?.partName?.let {
                    add(Val(it, ValType.STRING_USER_AGNOSTIC))
                }
            }
        }

        val plan = when (request) {
            is AgentInstructionRequest.DoerRequest -> buildDoerPlan(request)
            is AgentInstructionRequest.ReviewerRequest -> buildReviewerPlan(request)
            is AgentInstructionRequest.PlannerRequest -> buildPlannerPlan(request)
            is AgentInstructionRequest.PlanReviewerRequest -> buildPlanReviewerPlan(request)
        }
        return assembler.assembleFromPlan(plan, request)
    }

    // -- Doer plan --

    private fun buildDoerPlan(
        request: AgentInstructionRequest.DoerRequest,
    ): List<InstructionSection> = buildList {
        add(InstructionSection.RoleDefinition)
        add(InstructionSection.PrivateMd)
        add(InstructionSection.PartContext)
        add(InstructionSection.Ticket)
        add(InstructionSection.PlanMd)
        add(InstructionSection.PriorPublicMd)
        add(InstructionSection.IterationFeedback)
        add(InstructionSection.OutputPathSection("PUBLIC.md", request.publicMdOutputPath))
        add(InstructionSection.WritingGuidelines)
        add(InstructionSection.CallbackHelp(forReviewer = false, includePlanValidation = false))
    }

    // -- Reviewer plan --

    private fun buildReviewerPlan(
        request: AgentInstructionRequest.ReviewerRequest,
    ): List<InstructionSection> = buildList {
        add(InstructionSection.RoleDefinition)
        add(InstructionSection.PrivateMd)
        add(InstructionSection.PartContext)
        add(InstructionSection.Ticket)
        add(InstructionSection.PlanMd)
        add(InstructionSection.PriorPublicMd)
        add(InstructionSection.InlineFileContentSection(
            heading = "Doer Output (for review)",
            path = request.doerPublicMdPath,
        ))
        add(InstructionSection.StructuredFeedbackFormat)
        if (request.iterationNumber > 1) {
            add(
                InstructionSection.FeedbackDirectorySection(
                    dir = request.feedbackDir.resolve(ProtocolVocabulary.FeedbackStatus.ADDRESSED),
                    header = InstructionText.ADDRESSED_FEEDBACK_HEADER,
                )
            )
            add(
                InstructionSection.FeedbackDirectorySection(
                    dir = request.feedbackDir.resolve(ProtocolVocabulary.FeedbackStatus.REJECTED),
                    header = InstructionText.REJECTED_FEEDBACK_HEADER,
                )
            )
            add(
                InstructionSection.FeedbackDirectorySection(
                    dir = request.feedbackDir.resolve(ProtocolVocabulary.FeedbackStatus.PENDING),
                    header = InstructionText.SKIPPED_OPTIONAL_HEADER,
                    filenamePrefix = ProtocolVocabulary.SeverityPrefix.OPTIONAL,
                )
            )
        }
        add(InstructionSection.FeedbackWritingInstructions)
        add(InstructionSection.OutputPathSection("PUBLIC.md", request.publicMdOutputPath))
        add(InstructionSection.WritingGuidelines)
        add(InstructionSection.CallbackHelp(forReviewer = true, includePlanValidation = false))
    }

    // -- Planner plan --

    private fun buildPlannerPlan(
        request: AgentInstructionRequest.PlannerRequest,
    ): List<InstructionSection> = buildList {
        add(InstructionSection.RoleDefinition)
        add(InstructionSection.PrivateMd)
        add(InstructionSection.Ticket)
        add(InstructionSection.RoleCatalog)
        add(InstructionSection.AvailableAgentTypes)
        add(InstructionSection.PlanFormatInstructions)
        if (request.iterationNumber > 1 && request.planReviewerPublicMdPath != null) {
            add(InstructionSection.InlineFileContentSection("Reviewer Feedback", request.planReviewerPublicMdPath))
        }
        add(InstructionSection.OutputPathSection("plan_flow.json", request.planJsonOutputPath))
        add(InstructionSection.OutputPathSection("PLAN.md", request.planMdOutputPath))
        add(InstructionSection.OutputPathSection("PUBLIC.md", request.publicMdOutputPath))
        add(InstructionSection.WritingGuidelines)
        add(InstructionSection.CallbackHelp(forReviewer = false, includePlanValidation = true))
    }

    // -- Plan Reviewer plan --

    private fun buildPlanReviewerPlan(
        request: AgentInstructionRequest.PlanReviewerRequest,
    ): List<InstructionSection> = buildList {
        add(InstructionSection.RoleDefinition)
        add(InstructionSection.PrivateMd)
        add(InstructionSection.Ticket)
        add(InstructionSection.InlineStringContentSection(
            heading = "plan_flow.json",
            content = request.planJsonContent,
            codeBlockLanguage = "json",
        ))
        add(InstructionSection.InlineStringContentSection(
            heading = "PLAN.md",
            content = request.planMdContent,
        ))
        add(InstructionSection.AvailableAgentTypes)
        add(InstructionSection.InlineFileContentSection(
            heading = "Planner's Rationale",
            path = request.plannerPublicMdPath,
        ))
        if (request.iterationNumber > 1 && request.priorPlanReviewerPublicMdPath != null) {
            add(InstructionSection.InlineFileContentSection(
                heading = "Your Prior Feedback",
                path = request.priorPlanReviewerPublicMdPath,
            ))
        }
        add(InstructionSection.OutputPathSection("PUBLIC.md", request.publicMdOutputPath))
        add(InstructionSection.WritingGuidelines)
        add(InstructionSection.CallbackHelp(forReviewer = true, includePlanValidation = true))
    }

    // -- Utility extension --

    private val AgentInstructionRequest.executionContextOrNull: ExecutionContext?
        get() = when (this) {
            is AgentInstructionRequest.DoerRequest -> executionContext
            is AgentInstructionRequest.ReviewerRequest -> executionContext
            is AgentInstructionRequest.PlannerRequest -> null
            is AgentInstructionRequest.PlanReviewerRequest -> null
        }
}
