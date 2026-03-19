package com.glassthought.shepherd.integtest

import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid

/**
 * Wrapping [AgentTypeAdapter] that injects `TICKET_SHEPHERD_SERVER_PORT` and
 * `callback_shepherd.signal.sh` PATH into the command built by the delegate.
 *
 * This enables E2E tests to provide the dynamically-assigned server port
 * and callback script location to the spawned agent's tmux session.
 *
 * COUPLING NOTE: This adapter assumes the delegate ([ClaudeCodeAdapter]) produces a command
 * in the format `bash -c '<inner command>'`. If ClaudeCodeAdapter changes its command format
 * (e.g., different quoting style or shell invocation), this injection will break. The `check`
 * call below guards against silent failures, but the string manipulation is inherently fragile.
 *
 * Used by both [AgentFacadeImplIntegTest] and
 * `com.glassthought.shepherd.integtest.compaction.SelfCompactionIntegTest`.
 */
internal class ServerPortInjectingAdapter(
    private val delegate: AgentTypeAdapter,
    private val serverPort: Int,
    private val callbackScriptsDir: String,
) : AgentTypeAdapter {

    override fun buildStartCommand(
        params: BuildStartCommandParams,
    ): TmuxStartCommand {
        val delegateCommand = delegate.buildStartCommand(params)
        val originalCommand = delegateCommand.command

        val envPrefix =
            "export TICKET_SHEPHERD_SERVER_PORT=$serverPort && " +
                "export PATH=\$PATH:$callbackScriptsDir && "

        val bashCPrefix = "bash -c '"
        val insertionPoint = originalCommand.indexOf(bashCPrefix)
        check(insertionPoint >= 0) {
            "Expected command to contain '$bashCPrefix' " +
                "but got: $originalCommand"
        }
        val afterPrefix = insertionPoint + bashCPrefix.length
        val modifiedCommand =
            originalCommand.substring(0, afterPrefix) +
                envPrefix +
                originalCommand.substring(afterPrefix)

        return TmuxStartCommand(modifiedCommand)
    }

    override suspend fun resolveSessionId(
        handshakeGuid: HandshakeGuid,
    ): String {
        return delegate.resolveSessionId(handshakeGuid)
    }
}
