package com.glassthought.shepherd.core.creator

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.TicketShepherd
import com.glassthought.shepherd.core.TicketShepherdDeps
import com.glassthought.shepherd.core.agent.tmux.TmuxAllSessionsKiller
import com.glassthought.shepherd.core.executor.PartExecutorFactoryContext
import com.glassthought.shepherd.core.executor.PartExecutorFactoryCreator
import com.glassthought.shepherd.core.executor.ProductionPartExecutorFactoryCreator
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultProcessExiter
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.interrupt.InterruptHandlerImpl
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.CurrentStateInitializer
import com.glassthought.shepherd.core.state.CurrentStateInitializerImpl
import com.glassthought.shepherd.core.state.CurrentStatePersistence
import com.glassthought.shepherd.core.state.CurrentStatePersistenceImpl
import com.glassthought.shepherd.core.state.PlanFlowConverterImpl
import com.glassthought.shepherd.core.supporting.git.BranchNameBuilder
import com.glassthought.shepherd.core.supporting.git.GitBranchManager
import com.glassthought.shepherd.core.supporting.git.TryNResolver
import com.glassthought.shepherd.core.supporting.git.WorkingTreeValidator
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import com.glassthought.shepherd.core.supporting.ticket.TicketParser
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.core.time.SystemClock
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import com.glassthought.shepherd.core.workflow.WorkflowParser
import com.glassthought.shepherd.core.supporting.git.GitOperationFailureUseCase
import com.glassthought.shepherd.core.supporting.git.StandardGitIndexLockFileOperations
import com.glassthought.shepherd.usecase.finalcommit.FinalCommitUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCaseImpl
import com.glassthought.shepherd.usecase.healthmonitoring.NoOpTicketFailureLearningUseCase
import com.glassthought.shepherd.usecase.planning.DetailedPlanningUseCase
import com.glassthought.shepherd.usecase.planning.DetailedPlanningUseCaseImpl
import com.glassthought.shepherd.usecase.planning.ProductionPlanningPartExecutorFactory
import com.glassthought.shepherd.usecase.planning.SetupPlanUseCase
import com.glassthought.shepherd.usecase.planning.SetupPlanUseCaseImpl
import com.glassthought.shepherd.usecase.planning.StraightforwardPlanUseCaseImpl
import com.glassthought.shepherd.usecase.ticketstatus.TicketStatusUpdater
import com.glassthought.shepherd.usecase.ticketstatus.TicketStatusUpdaterImpl
import java.nio.file.Path

/**
 * Creates a fully wired [TicketShepherd] (ref.ap.P3po8Obvcjw4IXsSUSU91.E) for a single run.
 *
 * Receives [ShepherdContext] (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) plus ticket-specific inputs
 * (ticket path, workflow name), resolves ticket-scoped dependencies, and returns a ready-to-go
 * [TicketShepherd]. One shepherd per run — the creator is called once from the CLI entry point.
 *
 * See spec: ref.ap.cJbeC4udcM3J8UFoWXfGh.E (doc/core/TicketShepherdCreator.md)
 *
 * ap.cJbeC4udcM3J8UFoWXfGh.E
 */
@AnchorPoint("ap.cJbeC4udcM3J8UFoWXfGh.E")
fun interface TicketShepherdCreator {

    /**
     * Wires all ticket-scoped dependencies and returns a ready-to-run [TicketShepherd].
     *
     * Sequential steps:
     * 1. Resolve workflow JSON
     * 2. Parse ticket
     * 3. Validate ticket frontmatter (id, title, status)
     * 4. Validate ticket status is `in_progress`
     * 5. Validate working tree is clean
     * 6. Record originating branch
     * 7. Resolve try-N
     * 8. Create feature branch
     * 9. Set up `.ai_out/` directory structure
     * 10. Create in-memory CurrentState, flush to disk
     * 11. Construct all deps and wire TicketShepherd
     *
     * @param shepherdContext Shared infrastructure (tmux, logging, agent runner)
     * @param ticketPath Path to the ticket markdown file
     * @param workflowName Workflow name (e.g., "straightforward", "with-planning")
     * @return A fully wired [TicketShepherd] ready for [TicketShepherd.run]
     * @throws IllegalStateException on validation failures
     */
    suspend fun create(
        shepherdContext: ShepherdContext,
        ticketPath: Path,
        workflowName: String,
    ): TicketShepherd
}

/**
 * Production implementation of [TicketShepherdCreator].
 *
 * Constructor-injects shared/reusable dependencies. Ticket-scoped dependencies
 * (CurrentState, AiOutputStructure, AgentFacade, PartExecutorFactory) are constructed
 * internally within [create]/[wireTicketShepherd].
 *
 * @param workflowParser Parses workflow JSON into [WorkflowDefinition]
 * @param ticketParser Parses ticket markdown into [TicketData]
 * @param workingTreeValidator Validates git working tree is clean
 * @param tryNResolver Resolves the next try-N number
 * @param gitBranchManager Creates branches, queries current branch
 * @param currentStateInitializer Creates initial [CurrentState] from workflow definition
 * @param clock Wall-clock abstraction. Production: [SystemClock]; tests: TestClock
 * @param consoleOutput Console printing abstraction for testability
 * @param processExiter Process exit abstraction for testability
 * @param setupPlanUseCaseFactory Creates ticket-scoped [SetupPlanUseCase]
 * @param partExecutorFactoryCreator Creates ticket-scoped
 *   [com.glassthought.shepherd.core.executor.PartExecutorFactory] from deps available inside [wireTicketShepherd].
 *   Production default: [ProductionPartExecutorFactoryCreator] which wires AgentFacadeImpl, GitCommitStrategy, etc.
 * @param finalCommitUseCaseFactory Creates [FinalCommitUseCase] from deps available inside [wireTicketShepherd]
 * @param ticketStatusUpdaterFactory Creates [TicketStatusUpdater] for a given ticket ID
 * @param allSessionsKillerFactory Creates [AllSessionsKiller] from [ShepherdContext]
 * @param processRunnerFactory Creates [ProcessRunner] from [OutFactory]. Default: [ProcessRunner.standard]
 */
@Suppress("LongParameterList")
class TicketShepherdCreatorImpl(
    private val workflowParser: WorkflowParser,
    private val ticketParser: TicketParser,
    private val workingTreeValidator: WorkingTreeValidator,
    private val tryNResolver: TryNResolver,
    private val gitBranchManager: GitBranchManager,
    private val currentStateInitializer: CurrentStateInitializer = CurrentStateInitializerImpl(),
    private val clock: Clock = SystemClock(),
    private val consoleOutput: ConsoleOutput = DefaultConsoleOutput(),
    private val processExiter: ProcessExiter = DefaultProcessExiter(),
    private val setupPlanUseCaseFactory: SetupPlanUseCaseFactory = SetupPlanUseCaseFactory { ctx ->
        val wd = ctx.workflowDefinition
        val of = ctx.outFactory
        SetupPlanUseCaseImpl(
            workflowDefinition = wd,
            straightforwardPlanUseCase = StraightforwardPlanUseCaseImpl(
                parts = wd.parts ?: emptyList(),
                outFactory = of,
            ),
            detailedPlanningUseCase = wireDetailedPlanningUseCase(ctx),
            outFactory = of,
        )
    },
    private val partExecutorFactoryCreator: PartExecutorFactoryCreator =
        ProductionPartExecutorFactoryCreator(clock = clock),
    private val finalCommitUseCaseFactory: FinalCommitUseCaseFactory = FinalCommitUseCaseFactory { of, pr, gofu ->
        FinalCommitUseCase.standard(
            outFactory = of,
            processRunner = pr,
            gitOperationFailureUseCase = gofu,
        )
    },
    private val ticketStatusUpdaterFactory: TicketStatusUpdaterFactory =
        TicketStatusUpdaterFactory { ticketId, outFactory ->
            val processRunner = ProcessRunner.standard(outFactory)
            TicketStatusUpdaterImpl(
                ticketId = ticketId,
                processRunner = processRunner,
                outFactory = outFactory,
            )
        },
    private val allSessionsKillerFactory: AllSessionsKillerFactory = AllSessionsKillerFactory { ctx ->
        TmuxAllSessionsKiller(
            outFactory = ctx.infra.outFactory,
            tmuxCommandRunner = ctx.infra.tmux.commandRunner,
        )
    },
    private val processRunnerFactory: ProcessRunnerFactory = ProcessRunnerFactory { ProcessRunner.standard(it) },
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")),
) : TicketShepherdCreator {

    override suspend fun create(
        shepherdContext: ShepherdContext,
        ticketPath: Path,
        workflowName: String,
    ): TicketShepherd {
        // Steps 1-4: Parse and validate inputs
        val workflowDefinition = workflowParser.parse(workflowName, repoRoot)
        val ticketData = parseAndValidateTicket(ticketPath)

        // Step 5: Validate working tree is clean
        workingTreeValidator.validate()

        // Steps 6-8: Git operations (originating branch, try-N, feature branch)
        val gitResult = performGitSetup(ticketData)

        // Steps 9-10: Set up .ai_out/ and CurrentState
        val stateResult = setupStateAndStructure(workflowDefinition, gitResult.branchName)

        // Step 11: Wire TicketShepherd
        return wireTicketShepherd(
            shepherdContext = shepherdContext,
            ticketData = ticketData,
            workflowDefinition = workflowDefinition,
            gitResult = gitResult,
            stateResult = stateResult,
        )
    }

    // ── Private helper methods ──────────────────────────────────────────

    private suspend fun parseAndValidateTicket(ticketPath: Path): TicketData {
        val ticketData = ticketParser.parse(ticketPath)
        validateTicketFrontmatter(ticketData, ticketPath)
        validateTicketStatus(ticketData, ticketPath)
        return ticketData
    }

    private suspend fun performGitSetup(ticketData: TicketData): GitSetupResult {
        val originatingBranch = gitBranchManager.getCurrentBranch()
        val tryNumber = tryNResolver.resolve(ticketData)
        val branchName = BranchNameBuilder.build(ticketData, tryNumber)
        gitBranchManager.createAndCheckout(branchName)
        return GitSetupResult(originatingBranch, tryNumber, branchName)
    }

    private suspend fun setupStateAndStructure(
        workflowDefinition: WorkflowDefinition,
        branchName: String,
    ): StateSetupResult {
        val aiOutputStructure = AiOutputStructure(repoRoot, branchName)
        aiOutputStructure.ensureStructure(workflowDefinition.allPartsForStructure())

        val currentState = currentStateInitializer.createInitialState(workflowDefinition)
        val currentStatePersistence = CurrentStatePersistenceImpl(aiOutputStructure)
        currentStatePersistence.flush(currentState)

        return StateSetupResult(aiOutputStructure, currentState, currentStatePersistence)
    }

    private suspend fun wireTicketShepherd(
        shepherdContext: ShepherdContext,
        ticketData: TicketData,
        workflowDefinition: WorkflowDefinition,
        gitResult: GitSetupResult,
        stateResult: StateSetupResult,
    ): TicketShepherd {
        val outFactory = shepherdContext.infra.outFactory
        val ticketStatusUpdater = ticketStatusUpdaterFactory.create(ticketData.id, outFactory)
        val allSessionsKiller = allSessionsKillerFactory.create(shepherdContext)

        val interruptHandler = InterruptHandlerImpl(
            clock = clock,
            allSessionsKiller = allSessionsKiller,
            currentState = stateResult.currentState,
            currentStatePersistence = stateResult.currentStatePersistence,
            consoleOutput = consoleOutput,
            processExiter = processExiter,
        )

        val failedToExecutePlanUseCase = FailedToExecutePlanUseCaseImpl(
            outFactory = outFactory,
            consoleOutput = consoleOutput,
            allSessionsKiller = allSessionsKiller,
            ticketFailureLearningUseCase = NoOpTicketFailureLearningUseCase(),
            processExiter = processExiter,
        )

        val setupPlanUseCaseContext = SetupPlanUseCaseContext(
            workflowDefinition = workflowDefinition,
            outFactory = outFactory,
            shepherdContext = shepherdContext,
            aiOutputStructure = stateResult.aiOutputStructure,
            currentState = stateResult.currentState,
            currentStatePersistence = stateResult.currentStatePersistence,
            ticketData = ticketData,
            failedToExecutePlanUseCase = failedToExecutePlanUseCase,
            repoRoot = repoRoot,
        )

        val setupPlanUseCase = setupPlanUseCaseFactory.create(setupPlanUseCaseContext)

        val finalCommitUseCase = wireFinalCommitUseCase(outFactory, failedToExecutePlanUseCase)

        val partExecutorFactory = wirePartExecutorFactory(
            shepherdContext, outFactory, workflowDefinition, stateResult, ticketData,
        )

        val deps = TicketShepherdDeps(
            setupPlanUseCase = setupPlanUseCase,
            failedToExecutePlanUseCase = failedToExecutePlanUseCase,
            interruptHandler = interruptHandler,
            allSessionsKiller = allSessionsKiller,
            partExecutorFactory = partExecutorFactory,
            consoleOutput = consoleOutput,
            processExiter = processExiter,
            finalCommitUseCase = finalCommitUseCase,
            ticketStatusUpdater = ticketStatusUpdater,
            aiOutputStructure = stateResult.aiOutputStructure,
            out = outFactory.getOutForClass(TicketShepherd::class),
            ticketId = ticketData.id,
        )

        return TicketShepherd(
            deps = deps,
            currentState = stateResult.currentState,
            originatingBranch = gitResult.originatingBranch,
            tryNumber = gitResult.tryNumber,
        )
    }

    private fun wireFinalCommitUseCase(
        outFactory: OutFactory,
        failedToExecutePlanUseCase: FailedToExecutePlanUseCaseImpl,
    ): FinalCommitUseCase {
        val processRunner = processRunnerFactory.create(outFactory)
        val gitOperationFailureUseCase = GitOperationFailureUseCase.standard(
            outFactory = outFactory,
            processRunner = processRunner,
            failedToExecutePlanUseCase = failedToExecutePlanUseCase,
            indexLockFileOperations = StandardGitIndexLockFileOperations(repoRoot.resolve(".git")),
        )
        return finalCommitUseCaseFactory.create(
            outFactory = outFactory,
            processRunner = processRunner,
            gitOperationFailureUseCase = gitOperationFailureUseCase,
        )
    }

    private suspend fun wirePartExecutorFactory(
        shepherdContext: ShepherdContext,
        outFactory: OutFactory,
        workflowDefinition: WorkflowDefinition,
        stateResult: StateSetupResult,
        ticketData: TicketData,
    ) = partExecutorFactoryCreator.create(
        PartExecutorFactoryContext(
            shepherdContext = shepherdContext,
            outFactory = outFactory,
            aiOutputStructure = stateResult.aiOutputStructure,
            ticketData = ticketData,
            planMdPath = if (workflowDefinition.isWithPlanning) {
                stateResult.aiOutputStructure.planMd()
            } else {
                null
            },
            repoRoot = repoRoot,
        )
    )

    companion object {
        private const val REQUIRED_STATUS = "in_progress"
        private const val MAX_PLAN_CONVERSION_RETRIES = 3

        /**
         * Wires the production [DetailedPlanningUseCaseImpl] from [SetupPlanUseCaseContext].
         *
         * For straightforward workflows, this is never invoked (routing happens in [SetupPlanUseCaseImpl]).
         * For with-planning workflows, constructs the full planning infra:
         * [ProductionPlanningPartExecutorFactory], [PlanFlowConverterImpl], and [DetailedPlanningUseCaseImpl].
         */
        internal suspend fun wireDetailedPlanningUseCase(ctx: SetupPlanUseCaseContext): DetailedPlanningUseCase {
            val planningPart = ctx.workflowDefinition.planningParts?.firstOrNull()

            // WHY: For straightforward workflows, planningParts is null. The DetailedPlanningUseCase
            // will never be called in that case (SetupPlanUseCaseImpl routes to straightforward),
            // but we still need to provide an instance. Return a stub that fails explicitly.
            if (planningPart == null) {
                return DetailedPlanningUseCase {
                    error(
                        "DetailedPlanningUseCase invoked but no planning parts defined in workflow. " +
                            "This is a bug — SetupPlanUseCaseImpl should have routed to straightforward."
                    )
                }
            }

            val planningPartExecutorFactory = ProductionPlanningPartExecutorFactory.create(
                planningPart = planningPart,
                shepherdContext = ctx.shepherdContext,
                outFactory = ctx.outFactory,
                aiOutputStructure = ctx.aiOutputStructure,
                ticketData = ctx.ticketData,
                repoRoot = ctx.repoRoot,
                failedToExecutePlanUseCase = ctx.failedToExecutePlanUseCase,
            )

            val planFlowConverter = PlanFlowConverterImpl(
                aiOutputStructure = ctx.aiOutputStructure,
                currentStatePersistence = ctx.currentStatePersistence,
                outFactory = ctx.outFactory,
            )

            return DetailedPlanningUseCaseImpl(
                partExecutorFactory = planningPartExecutorFactory,
                planFlowConverter = planFlowConverter,
                failedToExecutePlanUseCase = ctx.failedToExecutePlanUseCase,
                currentState = ctx.currentState,
                maxConversionRetries = MAX_PLAN_CONVERSION_RETRIES,
                outFactory = ctx.outFactory,
            )
        }

        /**
         * Validates that the ticket has all required frontmatter fields.
         *
         * @throws IllegalStateException if any required field is missing or empty
         */
        internal fun validateTicketFrontmatter(ticketData: TicketData, ticketPath: Path) {
            val missingFields = buildList {
                if (ticketData.id.isBlank()) add("id")
                if (ticketData.title.isBlank()) add("title")
                if (ticketData.status.isNullOrBlank()) add("status")
            }

            check(missingFields.isEmpty()) {
                "Ticket at [$ticketPath] is missing required frontmatter field(s): " +
                    "${missingFields.joinToString(", ")}. " +
                    "All of [id, title, status] must be present and non-empty."
            }
        }

        /**
         * Validates that the ticket status is [REQUIRED_STATUS].
         *
         * @throws IllegalStateException if status is not `in_progress`
         */
        internal fun validateTicketStatus(ticketData: TicketData, ticketPath: Path) {
            check(ticketData.status == REQUIRED_STATUS) {
                "Ticket at [$ticketPath] has status=[${ticketData.status}] " +
                    "but must be [$REQUIRED_STATUS]. " +
                    "Mark the ticket as in_progress before running 'shepherd run'."
            }
        }
    }
}

// ── Internal result types ───────────────────────────────────────────────

/** Groups git setup results to avoid passing many individual values. */
private data class GitSetupResult(
    val originatingBranch: String,
    val tryNumber: Int,
    val branchName: String,
)

/** Groups state setup results to avoid passing many individual values. */
private data class StateSetupResult(
    val aiOutputStructure: AiOutputStructure,
    val currentState: CurrentState,
    val currentStatePersistence: CurrentStatePersistence,
)

// ── Factory interfaces for testability ──────────────────────────────────

/**
 * Ticket-scoped inputs needed to create a [SetupPlanUseCase].
 *
 * Groups all dependencies that become available inside `wireTicketShepherd()`
 * and are needed for both straightforward and detailed planning use cases.
 */
data class SetupPlanUseCaseContext(
    val workflowDefinition: WorkflowDefinition,
    val outFactory: OutFactory,
    val shepherdContext: ShepherdContext,
    val aiOutputStructure: AiOutputStructure,
    val currentState: CurrentState,
    val currentStatePersistence: CurrentStatePersistence,
    val ticketData: TicketData,
    val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    val repoRoot: Path,
)

/**
 * Factory for creating [SetupPlanUseCase] — ticket-scoped, depends on [SetupPlanUseCaseContext].
 */
fun interface SetupPlanUseCaseFactory {
    suspend fun create(context: SetupPlanUseCaseContext): SetupPlanUseCase
}

/**
 * Factory for creating [TicketStatusUpdater] for a specific ticket.
 *
 * The ticket ID is only known at [TicketShepherdCreator.create] time, so this factory
 * defers construction until the ticket has been parsed.
 */
fun interface TicketStatusUpdaterFactory {
    fun create(ticketId: String, outFactory: OutFactory): TicketStatusUpdater
}

/**
 * Factory for creating [AllSessionsKiller] from [ShepherdContext].
 */
fun interface AllSessionsKillerFactory {
    fun create(shepherdContext: ShepherdContext): AllSessionsKiller
}

/**
 * Factory for creating [ProcessRunner] from [OutFactory].
 */
fun interface ProcessRunnerFactory {
    fun create(outFactory: OutFactory): ProcessRunner
}

/**
 * Factory for creating [FinalCommitUseCase] from deps available inside [TicketShepherdCreatorImpl.wireTicketShepherd].
 */
fun interface FinalCommitUseCaseFactory {
    fun create(
        outFactory: OutFactory,
        processRunner: ProcessRunner,
        gitOperationFailureUseCase: GitOperationFailureUseCase,
    ): FinalCommitUseCase
}
