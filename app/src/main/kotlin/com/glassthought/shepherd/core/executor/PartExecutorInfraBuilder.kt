package com.glassthought.shepherd.core.executor

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.agent.contextwindow.ClaudeCodeContextWindowStateReader
import com.glassthought.shepherd.core.agent.facade.AgentFacadeImpl
import com.glassthought.shepherd.core.agent.rolecatalog.RoleCatalogLoader
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
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
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.SingleSessionKiller
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared infrastructure builder for [PartExecutor]-related wiring.
 *
 * Extracts the common infra-construction methods used by both
 * [ProductionPartExecutorFactoryCreator] (execution phase) and
 * [com.glassthought.shepherd.usecase.planning.ProductionPlanningPartExecutorFactory] (planning phase).
 *
 * All methods are stateless companions — no instance state needed.
 */
class PartExecutorInfraBuilder private constructor() {

    companion object {

        /**
         * Loads role definitions from the agents directory specified by the
         * [Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR] environment variable.
         *
         * @param outFactory Logging factory.
         * @param envProvider Environment variable reader.
         * @param roleCatalogLoader Optional custom loader. Defaults to [RoleCatalogLoader.standard].
         * @return Map of role name to [RoleDefinition].
         * @throws IllegalStateException if the environment variable is not set.
         */
        suspend fun loadRoleDefinitions(
            outFactory: OutFactory,
            envProvider: (String) -> String?,
            roleCatalogLoader: RoleCatalogLoader? = null,
        ): Map<String, RoleDefinition> {
            val loader = roleCatalogLoader ?: RoleCatalogLoader.standard(outFactory)
            val agentsDir = envProvider(Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR)
                ?: error(
                    "Environment variable [${Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR}] is not set. " +
                        "It must point to the directory containing agent role definition .md files."
                )
            val definitions = loader.load(Path.of(agentsDir))
            return definitions.associateBy { it.name }
        }

        /**
         * Builds an [AgentFacadeImpl] wired to the given [ShepherdContext] infrastructure.
         *
         * @param shepherdContext Shared infrastructure (tmux, logging, agent runner).
         * @param outFactory Logging factory.
         * @param clock Wall-clock abstraction.
         * @param sessionsState The shared session registry. Must be the SAME instance passed to
         *   [com.glassthought.shepherd.core.server.ShepherdServer].
         */
        fun buildAgentFacade(
            shepherdContext: ShepherdContext,
            outFactory: OutFactory,
            clock: Clock,
            sessionsState: SessionsState,
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
                sessionsState = sessionsState,
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

        /**
         * Builds a [GitCommitStrategy] that commits per sub-part with the given failure handler.
         *
         * @param outFactory Logging factory.
         * @param processRunner Process runner for git commands.
         * @param repoRoot Repository root path for git operations.
         * @param failedToExecutePlanUseCase Failure handler invoked on git operation failures.
         * @param envProvider Environment variable reader.
         */
        fun buildGitCommitStrategy(
            outFactory: OutFactory,
            processRunner: ProcessRunner,
            repoRoot: Path,
            failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
            envProvider: (String) -> String?,
        ): GitCommitStrategy {
            val hostUsername = envProvider(Constants.REQUIRED_ENV_VARS.HOST_USERNAME)
                ?: error(
                    "Environment variable [${Constants.REQUIRED_ENV_VARS.HOST_USERNAME}] is not set. " +
                        "It must identify the human operator for commit author attribution."
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
