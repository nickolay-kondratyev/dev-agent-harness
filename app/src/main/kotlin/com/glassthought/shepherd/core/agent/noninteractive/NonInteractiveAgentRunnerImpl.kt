package com.glassthought.shepherd.core.agent.noninteractive

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessCommandFailedException
import com.asgard.core.processRunner.ProcessCommandTimeoutException
import com.asgard.core.processRunner.ProcessRunner
import com.glassthought.shepherd.core.data.AgentType

/**
 * Runs agents as subprocesses in `--print` mode via [ProcessRunner].
 *
 * Since [ProcessRunner.runProcessV2] does not support working directory or env var injection,
 * commands are wrapped via `bash -c "cd {workDir} && ..."`.
 */
class NonInteractiveAgentRunnerImpl(
    private val processRunner: ProcessRunner,
    outFactory: OutFactory,
    private val zaiApiKey: String,
) : NonInteractiveAgentRunner {

    private val out = outFactory.getOutForClass(NonInteractiveAgentRunnerImpl::class)

    override suspend fun run(request: NonInteractiveAgentRequest): NonInteractiveAgentResult {
        out.info(
            "running_non_interactive_agent",
            Val(request.agentType.name, ValType.ENUM),
            Val(request.model, ValType.STRING_USER_AGNOSTIC),
        )

        val shellCommand = buildShellCommand(request)

        return try {
            val result = processRunner.runProcessV2(
                request.timeout,
                "bash", "-c", shellCommand,
            )
            val combinedOutput = combineOutput(result.stdout, result.stderr)
            NonInteractiveAgentResult.Success(combinedOutput)
        } catch (e: ProcessCommandFailedException) {
            val combinedOutput = combineOutput(e.result.stdout, e.result.stderr)
            NonInteractiveAgentResult.Failed(e.result.exitCode, combinedOutput)
        } catch (e: ProcessCommandTimeoutException) {
            val combinedOutput = combineOutput(e.partialResult.stdout, e.partialResult.stderr)
            NonInteractiveAgentResult.TimedOut(combinedOutput)
        }
    }

    internal fun buildShellCommand(request: NonInteractiveAgentRequest): String {
        val cdPrefix = "cd ${shellEscape(request.workingDirectory.toString())}"
        val agentCommand = when (request.agentType) {
            AgentType.CLAUDE_CODE -> buildClaudeCodeCommand(request)
            AgentType.PI -> buildPiCommand(request)
        }
        return "$cdPrefix && $agentCommand"
    }

    private fun buildClaudeCodeCommand(request: NonInteractiveAgentRequest): String {
        return "claude --print --model ${shellEscape(request.model)} -p ${shellEscape(request.instructions)}"
    }

    private fun buildPiCommand(request: NonInteractiveAgentRequest): String {
        val exportKey = "export ZAI_API_KEY=${shellEscape(zaiApiKey)}"
        val piCmd = "pi --provider zai --model ${shellEscape(request.model)} -p ${shellEscape(request.instructions)}"
        return "$exportKey && $piCmd"
    }

    companion object {
        /**
         * Escapes a string for safe inclusion in a single-quoted shell argument.
         *
         * Wraps the value in single quotes, escaping any embedded single quotes
         * using the `'\''` idiom (end quote, escaped quote, start quote).
         */
        internal fun shellEscape(value: String): String {
            val escaped = value.replace("'", "'\\''")
            return "'$escaped'"
        }

        private fun combineOutput(stdout: String, stderr: String): String {
            return if (stderr.isBlank()) {
                stdout
            } else if (stdout.isBlank()) {
                stderr
            } else {
                "$stdout\n$stderr"
            }
        }
    }
}
