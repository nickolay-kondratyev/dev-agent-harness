package com.glassthought.shepherd.core

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.agent.tmux.TmuxAllSessionsKiller
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultConsoleOutput
import com.glassthought.shepherd.core.infra.DefaultProcessExiter
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.interrupt.InterruptHandler
import com.glassthought.shepherd.core.interrupt.InterruptHandlerImpl
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.CurrentStatePersistence
import com.glassthought.shepherd.core.state.CurrentStatePersistenceImpl
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.core.time.SystemClock
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller

/**
 * Result of [TicketShepherdCreator.create] — holds all ticket-scoped wired dependencies.
 *
 * Future tickets will expand this to include the full TicketShepherd (ref.ap.P3po8Obvcjw4IXsSUSU91.E).
 * For now, it captures the InterruptHandler wiring result.
 */
data class TicketShepherdCreatorResult(
    val interruptHandler: InterruptHandler,
    val currentState: CurrentState,
    val currentStatePersistence: CurrentStatePersistence,
)

/**
 * Creates a fully wired ticket-scoped execution environment.
 *
 * Receives [ShepherdContext] (ref.ap.TkpljsXvwC6JaAVnIq02He98.E) plus ticket-specific inputs,
 * resolves ticket-scoped dependencies, and returns wired components ready for execution.
 * One creation per run — called once from the CLI entry point.
 *
 * ### Current scope
 * Wires [InterruptHandler] (ref.ap.yWFAwVrZdx1UTDqDJmDpe.E) with all production dependencies.
 * Pure wiring only — no side effects. The caller is responsible for calling
 * [InterruptHandler.install] on the returned result before the main execution loop starts.
 *
 * ### Future responsibilities (TODOs)
 * - Workflow JSON resolution
 * - Ticket parsing and validation
 * - Git branch creation (try-N resolution)
 * - `.ai_out/` directory structure setup
 * - AgentFacadeImpl construction
 * - Full TicketShepherd construction
 *
 * ap.cJbeC4udcM3J8UFoWXfGh.E
 */
@AnchorPoint("ap.cJbeC4udcM3J8UFoWXfGh.E")
fun interface TicketShepherdCreator {

    /**
     * Wires ticket-scoped dependencies. Pure wiring — no side effects.
     *
     * The caller must call [TicketShepherdCreatorResult.interruptHandler].[InterruptHandler.install]
     * before the main execution loop starts.
     *
     * @return [TicketShepherdCreatorResult] with all wired components.
     */
    fun create(): TicketShepherdCreatorResult
}

/**
 * Production implementation of [TicketShepherdCreator].
 *
 * Uses constructor injection for all dependencies that vary between production and test.
 * Infrastructure dependencies come from [ShepherdContext]; ticket-scoped dependencies
 * are constructed internally or injected via constructor for testability.
 *
 * @param shepherdContext Shared infrastructure (tmux, logging). Already initialized by
 *   ContextInitializer (ref.ap.9zump9YISPSIcdnxEXZZX.E).
 * @param aiOutputStructure Ticket-scoped `.ai_out/` path resolver. Created by caller
 *   after branch name is known.
 * @param workflowDefinition Static workflow definition parsed from config. Provides the
 *   parts list for `.ai_out/` directory structure creation.
 * @param clock Wall-clock abstraction. Production: [SystemClock]; tests: TestClock.
 * @param consoleOutput Console printing abstraction for testability.
 * @param processExiter Process exit abstraction for testability.
 * @param allSessionsKillerFactory Factory to create [AllSessionsKiller] from context.
 *   Default uses [TmuxAllSessionsKiller]. Tests inject a fake.
 */
class TicketShepherdCreatorImpl(
    private val shepherdContext: ShepherdContext,
    private val aiOutputStructure: AiOutputStructure,
    private val workflowDefinition: WorkflowDefinition,
    private val clock: Clock = SystemClock(),
    private val consoleOutput: ConsoleOutput = DefaultConsoleOutput(),
    private val processExiter: ProcessExiter = DefaultProcessExiter(),
    private val allSessionsKillerFactory: (OutFactory) -> AllSessionsKiller = { outFactory ->
        TmuxAllSessionsKiller(
            outFactory = outFactory,
            tmuxCommandRunner = shepherdContext.infra.tmux.commandRunner,
        )
    },
) : TicketShepherdCreator {

    override fun create(): TicketShepherdCreatorResult {
        // Future steps (not yet implemented — see KDoc):
        // - Workflow JSON resolution, ticket parsing, git branch creation

        aiOutputStructure.ensureStructure(workflowDefinition.allPartsForStructure())

        val currentState = CurrentState(parts = mutableListOf())

        val currentStatePersistence = CurrentStatePersistenceImpl(
            aiOutputStructure = aiOutputStructure,
        )

        val allSessionsKiller = allSessionsKillerFactory(shepherdContext.infra.outFactory)

        val interruptHandler = InterruptHandlerImpl(
            clock = clock,
            allSessionsKiller = allSessionsKiller,
            currentState = currentState,
            currentStatePersistence = currentStatePersistence,
            consoleOutput = consoleOutput,
            processExiter = processExiter,
        )

        // Future: construct full TicketShepherd (ref.ap.P3po8Obvcjw4IXsSUSU91.E)
        // and wire AgentFacadeImpl, ContextForAgentProvider, PartExecutor, etc.

        return TicketShepherdCreatorResult(
            interruptHandler = interruptHandler,
            currentState = currentState,
            currentStatePersistence = currentStatePersistence,
        )
    }
}
