package com.glassthought.shepherd.usecase.healthmonitoring

import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.UserInputReader

/**
 * Prompts the operator when the iteration budget is exhausted,
 * offering a fixed increment of additional iterations.
 *
 * See spec at `doc/use-case/HealthMonitoring.md` § FailedToConvergeUseCase Detail.
 */
fun interface FailedToConvergeUseCase {
    /**
     * Prompts the operator to grant more iterations when iteration budget is exhausted.
     * Returns `true` if the user granted more iterations, `false` if they chose to abort.
     */
    suspend fun askForMoreIterations(currentMax: Int, iterationsUsed: Int): Boolean
}

/**
 * Default implementation of [FailedToConvergeUseCase].
 *
 * Displays a binary y/N prompt via [consoleOutput] and reads the operator's
 * response via [userInputReader]. Only "y" or "Y" is treated as acceptance;
 * all other input (including null, empty, "N") results in abort.
 */
class FailedToConvergeUseCaseImpl(
    private val consoleOutput: ConsoleOutput,
    private val userInputReader: UserInputReader,
    private val config: HarnessTimeoutConfig,
) : FailedToConvergeUseCase {

    override suspend fun askForMoreIterations(currentMax: Int, iterationsUsed: Int): Boolean {
        consoleOutput.printlnRed(
            "Iteration budget exhausted ($iterationsUsed/$currentMax). " +
                "Grant ${config.failedToConvergeIterationIncrement} more iterations? [y/N]"
        )

        val input = userInputReader.readLine()
        return input?.trim()?.uppercase() == ACCEPT_INPUT
    }

    companion object {
        private const val ACCEPT_INPUT = "Y"
    }
}
