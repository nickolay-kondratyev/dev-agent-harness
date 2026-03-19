package com.glassthought.shepherd.core.agent.noninteractive

import com.glassthought.shepherd.core.data.AgentType
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Lightweight agent invocation for utility tasks that don't need interactive TMUX sessions.
 *
 * Runs an agent as a subprocess in `--print` mode (non-interactive, run-and-exit).
 * No TMUX, no handshake, no health monitoring, no SessionsState.
 *
 * ref.ap.ad4vG4G2xMPiMHRreoYVr.E
 */
fun interface NonInteractiveAgentRunner {
    suspend fun run(request: NonInteractiveAgentRequest): NonInteractiveAgentResult
}

data class NonInteractiveAgentRequest(
    /** The instructions for the agent — passed as -p argument. */
    val instructions: String,

    /** Working directory for the subprocess. */
    val workingDirectory: Path,

    /** Which agent binary to use. */
    val agentType: AgentType,

    /** Model name (e.g., "sonnet", value of $AI_MODEL__ZAI__FAST). */
    val model: String,

    /** Kill the process after this duration. */
    val timeout: Duration,
)

sealed class NonInteractiveAgentResult {
    /** Agent exited with code 0. */
    data class Success(val output: String) : NonInteractiveAgentResult()

    /** Agent exited with non-zero code. */
    data class Failed(val exitCode: Int, val output: String) : NonInteractiveAgentResult()

    /** Process killed after timeout. */
    data class TimedOut(val output: String) : NonInteractiveAgentResult()
}
