package com.glassthought.shepherd.core.agent.noninteractive

import com.asgard.core.processRunner.PartialProcessResult
import com.asgard.core.processRunner.ProcessCommandFailedException
import com.asgard.core.processRunner.ProcessCommandTimeoutException
import com.asgard.core.processRunner.ProcessResult
import com.asgard.core.processRunner.ProcessRunner
import kotlin.time.Duration

/**
 * Behavior to execute when [FakeProcessRunner.runProcessV2] is called.
 *
 * Controls what the fake returns or throws, enabling targeted unit testing
 * of [NonInteractiveAgentRunnerImpl] without real subprocess execution.
 */
sealed class FakeProcessBehavior {
    data class Succeed(val stdout: String, val stderr: String = "") : FakeProcessBehavior()
    data class Fail(val exitCode: Int, val stdout: String, val stderr: String = "") : FakeProcessBehavior()
    data class Timeout(val stdout: String, val stderr: String = "") : FakeProcessBehavior()
}

/**
 * A test double for [ProcessRunner] that records invocations and returns
 * pre-configured results.
 *
 * Only [runProcessV2] is supported — [runProcess] and [runScript] throw
 * [UnsupportedOperationException] since [NonInteractiveAgentRunnerImpl] does not use them.
 */
class FakeProcessRunner(
    private val behavior: FakeProcessBehavior,
) : ProcessRunner {

    /** The command array passed to the most recent [runProcessV2] call. */
    var lastCommandArgs: Array<out String?>? = null
        private set

    /** The timeout passed to the most recent [runProcessV2] call. */
    var lastTimeout: Duration? = null
        private set

    override suspend fun runProcess(vararg input: String?): String {
        throw UnsupportedOperationException("FakeProcessRunner only supports runProcessV2")
    }

    override suspend fun runScript(script: com.asgard.core.file.File): String {
        throw UnsupportedOperationException("FakeProcessRunner only supports runProcessV2")
    }

    override suspend fun runProcessV2(timeout: Duration, vararg input: String?): ProcessResult {
        lastCommandArgs = input
        lastTimeout = timeout

        return when (val b = behavior) {
            is FakeProcessBehavior.Succeed -> ProcessResult(
                b.stdout, b.stderr, 0, 100L
            )
            is FakeProcessBehavior.Fail -> throw ProcessCommandFailedException(
                "Process failed with exit code ${b.exitCode}",
                ProcessResult(b.stdout, b.stderr, b.exitCode, 100L),
            )
            is FakeProcessBehavior.Timeout -> throw ProcessCommandTimeoutException(
                "Process timed out",
                PartialProcessResult(b.stdout, b.stderr, 100L),
            )
        }
    }
}
