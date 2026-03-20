package com.glassthought.shepherd.core.executor

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.agent.contextwindow.ClaudeCodeContextWindowStateReader
import com.glassthought.shepherd.core.agent.facade.AgentFacadeImpl
import com.glassthought.shepherd.core.agent.rolecatalog.RoleCatalogLoader
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.infra.DefaultUserInputReader
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.question.QaDrainAndDeliverUseCase
import com.glassthought.shepherd.core.question.QaAnswersFileWriterImpl
import com.glassthought.shepherd.core.question.StdinUserQuestionHandler
import com.glassthought.shepherd.core.server.AckedPayloadSenderImpl
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase
import com.glassthought.shepherd.core.supporting.git.StandardGitIndexLockFileOperations
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.usecase.healthmonitoring.AgentUnresponsiveUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.NoOpTicketFailureLearningUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultProcessExiter
import com.glassthought.shepherd.core.infra.ProcessExiter
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production implementation of [PartExecutorFactoryCreator].
 *
 * Constructs the full [AgentFacadeImpl], [ContextForAgentProvider], [GitCommitStrategy],
 * [FailedToConvergeUseCase], loads role definitions, and assembles [PartExecutorDeps]
 * from the ticket-scoped [PartExecutorFactoryContext].
 *
 * All infrastructure is constructed fresh per ticket run — no shared mutable state across runs.
 *
 * @param clock Wall-clock abstraction (shared across the harness).
 * @param consoleOutput Console printing for user prompts (FailedToConvergeUseCase).
 * @param processExiter Process exit abstraction for FailedToExecutePlanUseCase.
 * @param roleCatalogLoader Loads role definitions from disk. Nullable — defaults to standard impl.
 * @param processRunnerProvider Creates a [ProcessRunner] from [OutFactory]. Nullable — defaults to standard.
 * @param envProvider Reads environment variables. Injectable for testing.
 */
class ProductionPartExecutorFactoryCreator(
    private val clock: Clock,
    private val consoleOutput: ConsoleOutput = DefaultConsoleOutput(),
    private val processExiter: ProcessExiter = DefaultProcessExiter(),
    private val roleCatalogLoader: RoleCatalogLoader? = null,
    private val processRunnerProvider: ((OutFactory) -> ProcessRunner)? = null,
    private val envProvider: (String) -> String? = System::getenv,
) : PartExecutorFactoryCreator {

    override suspend fun create(context: PartExecutorFactoryContext): PartExecutorFactory {
        val outFactory = context.outFactory
        val shepherdContext = context.shepherdContext

        // -- Role definitions --
        val roleDefinitions = loadRoleDefinitions(outFactory)

        // -- AgentFacade --
        val agentFacade = buildAgentFacade(shepherdContext, outFactory)

        // -- ContextForAgentProvider --
        val contextForAgentProvider = ContextForAgentProvider.standard(
            outFactory = outFactory,
            aiOutputStructure = context.aiOutputStructure,
        )

        // -- GitCommitStrategy --
        val processRunner = processRunnerProvider?.invoke(outFactory)
            ?: ProcessRunner.standard(outFactory)
        val gitCommitStrategy = buildGitCommitStrategy(outFactory, processRunner, shepherdContext)

        // -- FailedToConvergeUseCase --
        val failedToConvergeUseCase = FailedToConvergeUseCaseImpl(
            consoleOutput = consoleOutput,
            userInputReader = DefaultUserInputReader(),
            config = shepherdContext.timeoutConfig,
        )

        // -- SubPartConfigBuilder --
        val planMdPath = context.planMdPath
        val subPartConfigBuilder = SubPartConfigBuilder(
            aiOutputStructure = context.aiOutputStructure,
            roleDefinitions = roleDefinitions,
            ticketContent = context.ticketData.description,
            planMdPath = planMdPath,
        )

        // -- Assemble PartExecutorDeps --
        val deps = PartExecutorDeps(
            agentFacade = agentFacade,
            contextForAgentProvider = contextForAgentProvider,
            gitCommitStrategy = gitCommitStrategy,
            failedToConvergeUseCase = failedToConvergeUseCase,
            outFactory = outFactory,
            harnessTimeoutConfig = shepherdContext.timeoutConfig,
        )

        return PartExecutorFactoryCreator.buildFactory(deps, subPartConfigBuilder)
    }

    private suspend fun loadRoleDefinitions(outFactory: OutFactory): Map<String, RoleDefinition> {
        val loader = roleCatalogLoader ?: RoleCatalogLoader.standard(outFactory)
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
    ): GitCommitStrategy {
        val hostUsername = envProvider(Constants.REQUIRED_ENV_VARS.HOST_USERNAME)
            ?: error(
                "Environment variable [${Constants.REQUIRED_ENV_VARS.HOST_USERNAME}] is not set. " +
                    "It must identify the human operator for commit author attribution."
            )

        val failedToExecutePlanUseCase = FailedToExecutePlanUseCaseImpl(
            outFactory = outFactory,
            consoleOutput = consoleOutput,
            allSessionsKiller = com.glassthought.shepherd.core.agent.tmux.TmuxAllSessionsKiller(
                outFactory = outFactory,
                tmuxCommandRunner = shepherdContext.infra.tmux.commandRunner,
            ),
            ticketFailureLearningUseCase = NoOpTicketFailureLearningUseCase(),
            processExiter = processExiter,
        )

        val gitOperationFailureUseCase = GitOperationFailureUseCase.standard(
            outFactory = outFactory,
            processRunner = processRunner,
            failedToExecutePlanUseCase = failedToExecutePlanUseCase,
            indexLockFileOperations = StandardGitIndexLockFileOperations(
                gitDir = Path.of(System.getProperty("user.dir"), ".git"),
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
