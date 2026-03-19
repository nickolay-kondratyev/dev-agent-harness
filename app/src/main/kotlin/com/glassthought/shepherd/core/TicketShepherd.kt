package com.glassthought.shepherd.core

import com.asgard.core.annotation.AnchorPoint
import com.asgard.core.data.value.Val
import com.asgard.core.out.Out
import com.glassthought.shepherd.core.executor.PartExecutor
import com.glassthought.shepherd.core.executor.PartExecutorFactory
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.interrupt.InterruptHandler
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.usecase.finalcommit.FinalCommitUseCase
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import com.glassthought.shepherd.usecase.planning.SetupPlanUseCase
import com.glassthought.shepherd.usecase.ticketstatus.TicketStatusUpdater

/**
 * Dependencies bundle for [TicketShepherd] — groups collaborators to stay within
 * the parameter-count threshold while preserving constructor injection.
 */
data class TicketShepherdDeps(
    val setupPlanUseCase: SetupPlanUseCase,
    val failedToExecutePlanUseCase: FailedToExecutePlanUseCase,
    val interruptHandler: InterruptHandler,
    val allSessionsKiller: AllSessionsKiller,
    val partExecutorFactory: PartExecutorFactory,
    val consoleOutput: ConsoleOutput,
    val processExiter: ProcessExiter,
    val finalCommitUseCase: FinalCommitUseCase,
    val ticketStatusUpdater: TicketStatusUpdater,
    val aiOutputStructure: AiOutputStructure,
    val out: Out,
    val ticketId: String,
)

/**
 * Central coordinator that drives a ticket through its entire workflow lifecycle.
 *
 * Sets up the plan, creates executors for each part, runs them in sequence, and handles results.
 * See spec: ref.ap.P3po8Obvcjw4IXsSUSU91.E (TicketShepherd.md)
 *
 * **Flow:**
 * 1. Install interrupt handler.
 * 2. `SetupPlanUseCase.setup()` -> `List<Part>`.
 * 3. For each part: create executor via [PartExecutorFactory], execute, handle [PartResult].
 * 4. On all parts completed: final commit, update ticket status, defensive session cleanup,
 *    print success in green, exit(0).
 *
 * **Never returns** — the process exits via [ProcessExiter] on both success and failure paths.
 *
 * ap.Kx7mN3pRvWqY8jZtL5cBd.E
 */
@AnchorPoint("ap.Kx7mN3pRvWqY8jZtL5cBd.E")
class TicketShepherd(
    private val deps: TicketShepherdDeps,
    private val currentState: CurrentState,
    val originatingBranch: String,
    val tryNumber: Int,
) {

    /**
     * The currently running executor. Single reference point for cancellation.
     * `null` between parts.
     */
    var activeExecutor: PartExecutor? = null
        private set

    /**
     * Drives the ticket workflow to completion or failure.
     *
     * **Never returns** — exits via [ProcessExiter.exit] on success, or via
     * [FailedToExecutePlanUseCase.handleFailure] on failure (which also exits).
     */
    suspend fun run(): Nothing {
        deps.interruptHandler.install()

        val parts = deps.setupPlanUseCase.setup()
        currentState.appendExecutionParts(parts)

        for (part in parts) {
            deps.out.info("executing_part") {
                listOf(Val(part.name, ShepherdValType.PART_NAME))
            }

            val executor = deps.partExecutorFactory.create(part)
            activeExecutor = executor
            val result = executor.execute()
            activeExecutor = null

            when (result) {
                is PartResult.Completed -> {
                    // Part completed successfully — sessions already killed by PartExecutorImpl.
                    // Move to next part.
                }
                is PartResult.FailedWorkflow,
                is PartResult.FailedToConverge,
                is PartResult.AgentCrashed -> {
                    // Never returns — handleFailure prints, kills sessions, and exits.
                    deps.failedToExecutePlanUseCase.handleFailure(result)
                }
            }
        }

        // All parts completed — workflow success.
        deps.finalCommitUseCase.commitIfDirty()
        deps.ticketStatusUpdater.markDone()
        deps.allSessionsKiller.killAllSessions()
        deps.consoleOutput.printlnGreen(successMessage(deps.ticketId))
        deps.processExiter.exit(EXIT_CODE_SUCCESS)
    }

    companion object {
        private const val EXIT_CODE_SUCCESS = 0

        internal fun successMessage(ticketId: String): String =
            "Workflow completed successfully for ticket $ticketId."
    }
}
