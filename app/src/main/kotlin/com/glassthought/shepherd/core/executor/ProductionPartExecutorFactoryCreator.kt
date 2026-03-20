package com.glassthought.shepherd.core.executor

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.agent.rolecatalog.RoleCatalogLoader
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultProcessExiter
import com.glassthought.shepherd.core.infra.DefaultUserInputReader
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.NoOpTicketFailureLearningUseCase

/**
 * Production implementation of [PartExecutorFactoryCreator].
 *
 * Constructs the full AgentFacadeImpl, [ContextForAgentProvider], GitCommitStrategy,
 * FailedToConvergeUseCase, loads role definitions, and assembles [PartExecutorDeps]
 * from the ticket-scoped [PartExecutorFactoryContext].
 *
 * Delegates shared infrastructure construction to [PartExecutorInfraBuilder].
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

        val roleDefinitions = PartExecutorInfraBuilder.loadRoleDefinitions(
            outFactory = outFactory,
            envProvider = envProvider,
            roleCatalogLoader = roleCatalogLoader,
        )

        val agentFacade = PartExecutorInfraBuilder.buildAgentFacade(
            shepherdContext = shepherdContext,
            outFactory = outFactory,
            clock = clock,
            sessionsState = shepherdContext.sessionsState,
        )

        val contextForAgentProvider = ContextForAgentProvider.standard(
            outFactory = outFactory,
            aiOutputStructure = context.aiOutputStructure,
        )

        val processRunner = processRunnerProvider?.invoke(outFactory)
            ?: ProcessRunner.standard(outFactory)

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

        val gitCommitStrategy = PartExecutorInfraBuilder.buildGitCommitStrategy(
            outFactory = outFactory,
            processRunner = processRunner,
            repoRoot = context.repoRoot,
            failedToExecutePlanUseCase = failedToExecutePlanUseCase,
            envProvider = envProvider,
        )

        val failedToConvergeUseCase = FailedToConvergeUseCaseImpl(
            consoleOutput = consoleOutput,
            userInputReader = DefaultUserInputReader(),
            config = shepherdContext.timeoutConfig,
        )

        val subPartConfigBuilder = SubPartConfigBuilder(
            aiOutputStructure = context.aiOutputStructure,
            roleDefinitions = roleDefinitions,
            ticketContent = context.ticketData.description,
            planMdPath = context.planMdPath,
        )

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
}
