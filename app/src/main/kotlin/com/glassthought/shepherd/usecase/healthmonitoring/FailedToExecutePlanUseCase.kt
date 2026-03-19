package com.glassthought.shepherd.usecase.healthmonitoring

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.state.PartResult

/**
 * Handles blocking plan-execution failures: prints the error, kills sessions,
 * records failure learning, and exits the process.
 *
 * See spec at `doc/use-case/HealthMonitoring.md` § FailedToExecutePlanUseCase Detail.
 */
interface FailedToExecutePlanUseCase {
    suspend fun handleFailure(failedResult: PartResult): Nothing
}

/**
 * Default implementation of [FailedToExecutePlanUseCase].
 *
 * Steps executed in order:
 * 1. Print failure reason in RED to console (per [PartResult] variant).
 * 2. Kill all TMUX sessions via [allSessionsKiller].
 * 3. Record failure learning (best-effort — exceptions logged as WARN).
 * 4. Exit with code 1 via [processExiter].
 */
class FailedToExecutePlanUseCaseImpl(
    outFactory: OutFactory,
    private val consoleOutput: ConsoleOutput,
    private val allSessionsKiller: AllSessionsKiller,
    private val ticketFailureLearningUseCase: TicketFailureLearningUseCase,
    private val processExiter: ProcessExiter,
) : FailedToExecutePlanUseCase {

    private val out = outFactory.getOutForClass(FailedToExecutePlanUseCaseImpl::class)

    override suspend fun handleFailure(failedResult: PartResult): Nothing {
        printFailureInRed(failedResult)

        allSessionsKiller.killAllSessions()

        tryRecordFailureLearning(failedResult)

        processExiter.exit(EXIT_CODE_FAILURE)
    }

    private fun printFailureInRed(failedResult: PartResult) {
        val message = when (failedResult) {
            is PartResult.FailedWorkflow -> "Workflow failed: ${failedResult.reason}"
            is PartResult.AgentCrashed -> "Agent crashed: ${failedResult.details}"
            is PartResult.FailedToConverge -> "Failed to converge: ${failedResult.summary}"
            is PartResult.Completed -> throw IllegalArgumentException(
                "Completed result should never reach FailedToExecutePlanUseCase"
            )
        }
        consoleOutput.printlnRed(message)
    }

    private suspend fun tryRecordFailureLearning(failedResult: PartResult) {
        try {
            ticketFailureLearningUseCase.recordFailureLearning(failedResult)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            out.warn(
                "ticket_failure_learning_failed",
                Val(e.message ?: "unknown", ValType.STRING_USER_AGNOSTIC),
            )
        }
    }

    companion object {
        private const val EXIT_CODE_FAILURE = 1
    }
}
