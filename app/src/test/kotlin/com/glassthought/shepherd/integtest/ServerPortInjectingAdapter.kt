package com.glassthought.shepherd.integtest

import com.glassthought.shepherd.core.agent.adapter.AgentTypeAdapter
import com.glassthought.shepherd.core.agent.adapter.BuildStartCommandParams
import com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter
import com.glassthought.shepherd.core.agent.data.TmuxStartCommand
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.initializer.ContextInitializer

/**
 * Wrapping [AgentTypeAdapter] that replaces sentinel `TICKET_SHEPHERD_SERVER_PORT` and
 * `PATH` exports in the command built by the delegate with real, dynamically-assigned values.
 *
 * This enables E2E tests to provide the dynamically-assigned server port
 * and callback script location to the spawned agent's tmux session.
 *
 * WHY replacement (not prepend): [ClaudeCodeAdapter] now exports `TICKET_SHEPHERD_SERVER_PORT`
 * and PATH natively in the command string. When the adapter is initialized with sentinel values
 * (via [ContextInitializer.forIntegTest]), those sentinels must be **replaced** — not prepended
 * before — because bash processes exports sequentially and later exports overwrite earlier ones.
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

        // Replace the sentinel SERVER_PORT export with the real port
        val sentinelPortExport = "export ${Constants.AGENT_COMM.SERVER_PORT_ENV_VAR}=$SENTINEL_PORT"
        val realPortExport = "export ${Constants.AGENT_COMM.SERVER_PORT_ENV_VAR}=$serverPort"
        check(originalCommand.contains(sentinelPortExport)) {
            "Expected command to contain sentinel port export '$sentinelPortExport' " +
                "but got: $originalCommand"
        }

        // Replace the sentinel PATH export with the real callback scripts dir
        val sentinelPathExport = "export PATH=\$PATH:$SENTINEL_SCRIPTS_DIR"
        val realPathExport = "export PATH=\$PATH:$callbackScriptsDir"
        check(originalCommand.contains(sentinelPathExport)) {
            "Expected command to contain sentinel PATH export '$sentinelPathExport' " +
                "but got: $originalCommand"
        }

        val modifiedCommand = originalCommand
            .replace(sentinelPortExport, realPortExport)
            .replace(sentinelPathExport, realPathExport)

        return TmuxStartCommand(modifiedCommand)
    }

    override suspend fun resolveSessionId(
        handshakeGuid: HandshakeGuid,
    ): String {
        return delegate.resolveSessionId(handshakeGuid)
    }

    companion object {
        private const val SENTINEL_PORT = ContextInitializer.INTEG_TEST_SENTINEL_PORT
        private const val SENTINEL_SCRIPTS_DIR = ContextInitializer.INTEG_TEST_SENTINEL_SCRIPTS_DIR
    }
}
