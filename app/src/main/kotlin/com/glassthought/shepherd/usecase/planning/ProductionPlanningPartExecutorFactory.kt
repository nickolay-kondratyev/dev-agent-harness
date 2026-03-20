package com.glassthought.shepherd.usecase.planning

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.executor.PartExecutor
import com.glassthought.shepherd.core.executor.PartExecutorDeps
import com.glassthought.shepherd.core.executor.PartExecutorFactoryCreator
import com.glassthought.shepherd.core.executor.PartExecutorImpl
import com.glassthought.shepherd.core.executor.PartExecutorInfraBuilder
import com.glassthought.shepherd.core.executor.SubPartConfigBuilder
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.DefaultUserInputReader
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.core.time.SystemClock
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import java.nio.file.Path

/**
 * Ticket-scoped context required to build a [ProductionPlanningPartExecutorFactory].
 *
 * Groups the six per-ticket dependencies so [ProductionPlanningPartExecutorFactory.create]
 * stays within the parameter-count limit.
 */
data class PlanningFactoryContext(
    val shepherdContext: ShepherdContext,
    val outFactory: OutFactory,
    val aiOutputStructure: AiOutputStructure,
    val ticketData: TicketData,
    val repoRoot: Path,
    val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
)

/**
 * Production implementation of [PlanningPartExecutorFactory].
 *
 * Constructs a [PartExecutorImpl] for the planning phase (PLANNER doer + optional PLAN_REVIEWER reviewer).
 * All heavy infrastructure (AgentFacade, GitCommitStrategy, SubPartConfigBuilder, etc.) is built
 * during factory construction via the suspend [create] companion factory method, so that the
 * non-suspend [PlanningPartExecutorFactory.create] can assemble the executor synchronously.
 *
 * Delegates shared infrastructure construction to [PartExecutorInfraBuilder].
 */
class ProductionPlanningPartExecutorFactory internal constructor(
    private val planningPart: Part,
    private val deps: PartExecutorDeps,
    private val subPartConfigBuilder: SubPartConfigBuilder,
) : PlanningPartExecutorFactory {

    override fun create(priorConversionErrors: List<String>): PartExecutor {
        // WHY-NOT: priorConversionErrors are not yet wired into instruction context (V2).
        // For now, we log a warning if non-empty so the behavior is transparent.
        if (priorConversionErrors.isNotEmpty()) {
            // NOTE: Out.warn is suspend, but create() is not suspend.
            // Using println here because PlanningPartExecutorFactory.create() contract is non-suspend.
            // This is a temporary V1 limitation — V2 will wire errors into the planner's context.
            println(
                "[WARN] ProductionPlanningPartExecutorFactory: " +
                    "priorConversionErrors not yet wired into planning context (V2). " +
                    "Errors: $priorConversionErrors"
            )
        }

        val doerConfig = subPartConfigBuilder.build(
            part = planningPart,
            subPartIndex = SubPartRole.DOER_INDEX,
            priorPublicMdPaths = emptyList(),
        )

        val reviewerConfig = if (planningPart.subParts.size > 1) {
            subPartConfigBuilder.build(
                part = planningPart,
                subPartIndex = SubPartRole.REVIEWER_INDEX,
                priorPublicMdPaths = emptyList(),
            )
        } else {
            null
        }

        return PartExecutorImpl(
            doerConfig = doerConfig,
            reviewerConfig = reviewerConfig,
            deps = deps,
            iterationConfig = PartExecutorFactoryCreator.resolveIterationConfig(planningPart),
        )
    }

    companion object {
        /**
         * Suspend factory method that builds all infrastructure and returns a ready-to-use
         * [ProductionPlanningPartExecutorFactory].
         *
         * @param planningPart The planning [Part] from WorkflowDefinition.planningParts.
         * @param context Ticket-scoped dependencies bundled in [PlanningFactoryContext].
         * @param clock Wall-clock abstraction. Default: [SystemClock].
         * @param envProvider Environment variable reader. Default: [System.getenv].
         */
        suspend fun create(
            planningPart: Part,
            context: PlanningFactoryContext,
            clock: Clock = SystemClock(),
            envProvider: (String) -> String? = System::getenv,
        ): ProductionPlanningPartExecutorFactory {
            val roleDefinitions = PartExecutorInfraBuilder.loadRoleDefinitions(
                outFactory = context.outFactory,
                envProvider = envProvider,
            )

            val agentFacade = PartExecutorInfraBuilder.buildAgentFacade(
                shepherdContext = context.shepherdContext,
                outFactory = context.outFactory,
                clock = clock,
                sessionsState = context.shepherdContext.sessionsState,
            )

            val contextForAgentProvider = ContextForAgentProvider.standard(
                outFactory = context.outFactory,
                aiOutputStructure = context.aiOutputStructure,
            )

            val processRunner = ProcessRunner.standard(context.outFactory)

            val gitCommitStrategy = PartExecutorInfraBuilder.buildGitCommitStrategy(
                outFactory = context.outFactory,
                processRunner = processRunner,
                repoRoot = context.repoRoot,
                failedToExecutePlanUseCase = context.failedToExecutePlanUseCase,
                envProvider = envProvider,
            )

            val failedToConvergeUseCase = FailedToConvergeUseCaseImpl(
                consoleOutput = com.glassthought.shepherd.core.infra.DefaultConsoleOutput(),
                userInputReader = DefaultUserInputReader(),
                config = context.shepherdContext.timeoutConfig,
            )

            val subPartConfigBuilder = SubPartConfigBuilder(
                aiOutputStructure = context.aiOutputStructure,
                roleDefinitions = roleDefinitions,
                ticketContent = context.ticketData.description,
                planMdPath = null, // WHY: Planning agents produce the plan — they don't read an existing plan.
            )

            val deps = PartExecutorDeps(
                agentFacade = agentFacade,
                contextForAgentProvider = contextForAgentProvider,
                gitCommitStrategy = gitCommitStrategy,
                failedToConvergeUseCase = failedToConvergeUseCase,
                outFactory = context.outFactory,
                harnessTimeoutConfig = context.shepherdContext.timeoutConfig,
            )

            return ProductionPlanningPartExecutorFactory(
                planningPart = planningPart,
                deps = deps,
                subPartConfigBuilder = subPartConfigBuilder,
            )
        }
    }
}
