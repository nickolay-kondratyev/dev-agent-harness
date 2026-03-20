package com.glassthought.shepherd.usecase.planning

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.agent.contextwindow.ClaudeCodeContextWindowStateReader
import com.glassthought.shepherd.core.agent.facade.AgentFacade
import com.glassthought.shepherd.core.agent.facade.AgentFacadeImpl
import com.glassthought.shepherd.core.agent.rolecatalog.RoleCatalogLoader
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.executor.PartExecutor
import com.glassthought.shepherd.core.executor.PartExecutorDeps
import com.glassthought.shepherd.core.executor.PartExecutorFactoryCreator
import com.glassthought.shepherd.core.executor.PartExecutorImpl
import com.glassthought.shepherd.core.executor.SubPartConfigBuilder
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.DefaultUserInputReader
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.question.QaDrainAndDeliverUseCase
import com.glassthought.shepherd.core.question.QaAnswersFileWriterImpl
import com.glassthought.shepherd.core.question.StdinUserQuestionHandler
import com.glassthought.shepherd.core.server.AckedPayloadSenderImpl
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase
import com.glassthought.shepherd.core.supporting.git.StandardGitIndexLockFileOperations
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.core.time.SystemClock
import com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.NoOpTicketFailureLearningUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production implementation of [PlanningPartExecutorFactory].
 *
 * Constructs a [PartExecutorImpl] for the planning phase (PLANNER doer + optional PLAN_REVIEWER reviewer).
 * All heavy infrastructure (AgentFacade, GitCommitStrategy, SubPartConfigBuilder, etc.) is built
 * during factory construction via the suspend [create] companion factory method, so that the
 * non-suspend [PlanningPartExecutorFactory.create] can assemble the executor synchronously.
 *
 * Follows the same infrastructure construction pattern as
 * [com.glassthought.shepherd.core.executor.ProductionPartExecutorFactoryCreator].
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
         * @param planningPart The planning [Part] from [WorkflowDefinition.planningParts].
         * @param shepherdContext Shared infrastructure (tmux, logging, agent runner).
         * @param outFactory Logging factory.
         * @param aiOutputStructure Ticket-scoped path resolver.
         * @param ticketData Parsed ticket data (description used in agent context).
         * @param repoRoot Repository root path for git operations.
         * @param clock Wall-clock abstraction. Default: [SystemClock].
         * @param envProvider Environment variable reader. Default: [System.getenv].
         */
        @Suppress("LongParameterList")
        suspend fun create(
            planningPart: Part,
            shepherdContext: ShepherdContext,
            outFactory: OutFactory,
            aiOutputStructure: AiOutputStructure,
            ticketData: TicketData,
            repoRoot: Path,
            clock: Clock = SystemClock(),
            envProvider: (String) -> String? = System::getenv,
        ): ProductionPlanningPartExecutorFactory {
            val roleDefinitions = loadRoleDefinitions(outFactory, envProvider)
            val agentFacade = buildAgentFacade(shepherdContext, outFactory, clock)
            val contextForAgentProvider = ContextForAgentProvider.standard(
                outFactory = outFactory,
                aiOutputStructure = aiOutputStructure,
            )
            val processRunner = ProcessRunner.standard(outFactory)
            val gitCommitStrategy = buildGitCommitStrategy(
                outFactory, processRunner, shepherdContext, repoRoot, envProvider,
            )
            val failedToConvergeUseCase = FailedToConvergeUseCaseImpl(
                consoleOutput = com.glassthought.shepherd.core.infra.DefaultConsoleOutput(),
                userInputReader = DefaultUserInputReader(),
                config = shepherdContext.timeoutConfig,
            )

            val subPartConfigBuilder = SubPartConfigBuilder(
                aiOutputStructure = aiOutputStructure,
                roleDefinitions = roleDefinitions,
                ticketContent = ticketData.description,
                planMdPath = null, // WHY: Planning agents produce the plan — they don't read an existing plan.
            )

            val deps = PartExecutorDeps(
                agentFacade = agentFacade,
                contextForAgentProvider = contextForAgentProvider,
                gitCommitStrategy = gitCommitStrategy,
                failedToConvergeUseCase = failedToConvergeUseCase,
                outFactory = outFactory,
                harnessTimeoutConfig = shepherdContext.timeoutConfig,
            )

            return ProductionPlanningPartExecutorFactory(
                planningPart = planningPart,
                deps = deps,
                subPartConfigBuilder = subPartConfigBuilder,
            )
        }

        private suspend fun loadRoleDefinitions(
            outFactory: OutFactory,
            envProvider: (String) -> String?,
        ): Map<String, RoleDefinition> {
            val loader = RoleCatalogLoader.standard(outFactory)
            val agentsDir = envProvider(Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR)
                ?: error(
                    "Environment variable [${Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR}] is not set. " +
                        "It must point to the directory containing agent role definition .md files."
                )
            val definitions = loader.load(Path.of(agentsDir))
            return definitions.associateBy { it.name }
        }

        private fun buildAgentFacade(
            shepherdContext: ShepherdContext,
            outFactory: OutFactory,
            clock: Clock,
        ): AgentFacadeImpl {
            val sessionManager = shepherdContext.infra.tmux.sessionManager
            val singleSessionKiller = SingleSessionKiller { session ->
                sessionManager.killSession(session)
            }

            val ackedPayloadSender = AckedPayloadSenderImpl(
                outFactory = outFactory,
                payloadCounter = AtomicInteger(0),
            )

            val agentUnresponsiveUseCase = AgentUnresponsiveUseCaseImpl(
                outFactory = outFactory,
                sessionKiller = singleSessionKiller,
            )

            val qaDrainer = QaDrainAndDeliverUseCase(
                userQuestionHandler = StdinUserQuestionHandler(),
                qaAnswersFileWriter = QaAnswersFileWriterImpl(),
                ackedPayloadSender = ackedPayloadSender,
                outFactory = outFactory,
            )

            val contextWindowStateReader = ClaudeCodeContextWindowStateReader(
                clock = clock,
                harnessTimeoutConfig = shepherdContext.timeoutConfig,
                outFactory = outFactory,
            )

            return AgentFacadeImpl(
                sessionsState = SessionsState(),
                agentTypeAdapter = shepherdContext.infra.claudeCode.agentTypeAdapter,
                tmuxSessionCreator = sessionManager,
                sessionKiller = singleSessionKiller,
                contextWindowStateReader = contextWindowStateReader,
                clock = clock,
                harnessTimeoutConfig = shepherdContext.timeoutConfig,
                ackedPayloadSender = ackedPayloadSender,
                agentUnresponsiveUseCase = agentUnresponsiveUseCase,
                qaDrainAndDeliverUseCase = qaDrainer,
                outFactory = outFactory,
            )
        }

        private fun buildGitCommitStrategy(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            shepherdContext: ShepherdContext,
            repoRoot: Path,
            envProvider: (String) -> String?,
        ): GitCommitStrategy {
            val hostUsername = envProvider(Constants.REQUIRED_ENV_VARS.HOST_USERNAME)
                ?: error(
                    "Environment variable [${Constants.REQUIRED_ENV_VARS.HOST_USERNAME}] is not set. " +
                        "It must identify the human operator for commit author attribution."
                )

            val failedToExecutePlanUseCase = FailedToExecutePlanUseCaseImpl(
                outFactory = outFactory,
                consoleOutput = com.glassthought.shepherd.core.infra.DefaultConsoleOutput(),
                allSessionsKiller = com.glassthought.shepherd.core.agent.tmux.TmuxAllSessionsKiller(
                    outFactory = outFactory,
                    tmuxCommandRunner = shepherdContext.infra.tmux.commandRunner,
                ),
                ticketFailureLearningUseCase = NoOpTicketFailureLearningUseCase(),
                processExiter = com.glassthought.shepherd.core.infra.DefaultProcessExiter(),
            )

            val gitOperationFailureUseCase = GitOperationFailureUseCase.standard(
                outFactory = outFactory,
                processRunner = processRunner,
                failedToExecutePlanUseCase = failedToExecutePlanUseCase,
                indexLockFileOperations = StandardGitIndexLockFileOperations(
                    gitDir = repoRoot.resolve(".git"),
                ),
            )

            return GitCommitStrategy.commitPerSubPart(
                outFactory = outFactory,
                processRunner = processRunner,
                gitOperationFailureUseCase = gitOperationFailureUseCase,
                hostUsername = hostUsername,
                gitUserEmail = "$hostUsername@shepherd.local",
            )
        }
    }
}
