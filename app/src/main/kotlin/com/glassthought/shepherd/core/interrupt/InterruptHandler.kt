package com.glassthought.shepherd.core.interrupt

import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.CurrentStatePersistence
import com.glassthought.shepherd.core.state.SubPartStatus
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import kotlinx.coroutines.runBlocking
import com.asgard.core.annotation.AnchorPoint
import sun.misc.Signal
import java.time.Instant

/**
 * Handles SIGINT (Ctrl+C) using the double-press pattern to prevent accidental termination.
 *
 * 1. First Ctrl+C  -> prints confirmation prompt, records timestamp. Execution continues.
 * 2. Second Ctrl+C within [CONFIRM_WINDOW_MS]ms -> cleanup and exit.
 * 3. Second Ctrl+C after window expires -> treated as fresh first Ctrl+C.
 *
 * See spec: `doc/core/TicketShepherd.md` section "Interrupt Protocol (Ctrl+C)".
 *
 * ap.yWFAwVrZdx1UTDqDJmDpe.E
 */
@AnchorPoint("ap.yWFAwVrZdx1UTDqDJmDpe.E")
fun interface InterruptHandler {
    /**
     * Registers the SIGINT handler. Called once during startup.
     * NOT suspend — signal registration is synchronous.
     */
    fun install()
}

/**
 * Default [InterruptHandler] implementation.
 *
 * WHY [runBlocking] in the signal callback: Signal handlers run on a dedicated signal-dispatch
 * thread where no coroutine scope exists. [AllSessionsKiller.killAllSessions] and
 * [CurrentStatePersistence.flush] are suspend functions. Using [runBlocking] is acceptable
 * in this shutdown-path context.
 */
class InterruptHandlerImpl(
    private val clock: Clock,
    private val allSessionsKiller: AllSessionsKiller,
    private val currentState: CurrentState,
    private val currentStatePersistence: CurrentStatePersistence,
    private val consoleOutput: ConsoleOutput,
    private val processExiter: ProcessExiter,
) : InterruptHandler {

    /**
     * Timestamp of the first (unconfirmed) Ctrl+C, or null if no pending press.
     *
     * WHY volatile: the signal handler runs on a different thread than the main application.
     */
    @Volatile
    internal var firstPressTimestamp: Instant? = null

    override fun install() {
        Signal.handle(Signal("INT")) {
            handleSignal()
        }
    }

    /**
     * Core double-Ctrl+C logic, extracted for testability (no JVM signal wiring needed in tests).
     */
    internal fun handleSignal() {
        val now = clock.now()
        val firstPress = firstPressTimestamp

        if (firstPress != null && isWithinConfirmWindow(now, firstPress)) {
            performCleanupAndExit()
        } else {
            firstPressTimestamp = now
            consoleOutput.printlnRed(CONFIRMATION_MESSAGE)
        }
    }

    private fun isWithinConfirmWindow(now: Instant, firstPress: Instant): Boolean {
        val elapsedMs = java.time.Duration.between(firstPress, now).toMillis()
        return elapsedMs < CONFIRM_WINDOW_MS
    }

    private fun performCleanupAndExit() {
        runBlocking {
            allSessionsKiller.killAllSessions()
            markInProgressSubPartsAsFailed()
            currentStatePersistence.flush(currentState)
        }
        processExiter.exit(EXIT_CODE_INTERRUPTED)
    }

    private fun markInProgressSubPartsAsFailed() {
        for (part in currentState.parts) {
            for (subPart in part.subParts) {
                if (subPart.status == SubPartStatus.IN_PROGRESS) {
                    currentState.updateSubPartStatus(part.name, subPart.name, SubPartStatus.FAILED)
                }
            }
        }
    }

    companion object {
        internal const val CONFIRM_WINDOW_MS = 2000L
        internal const val CONFIRMATION_MESSAGE = "Press Ctrl+C again to confirm exit."
        private const val EXIT_CODE_INTERRUPTED = 1
    }
}
