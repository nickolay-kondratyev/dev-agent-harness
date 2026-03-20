package com.glassthought.shepherd.core

object Constants {
  /** Claude Code CLI configuration. */
  object CLAUDE_CODE {
    /** Root directory where Claude Code stores JSONL session files.
     *  Used by [com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter] to scan for HandshakeGuid matches. */
    fun defaultProjectsDir(): java.nio.file.Path =
      java.nio.file.Path.of(System.getProperty("user.home"), ".claude", "projects")
  }

  /**
   * Agent↔harness communication constants.
   *
   * See agent-to-server-communication-protocol.md (ref.ap.wLpW8YbvqpRdxDplnN7Vh.E).
   */
  object AGENT_COMM {
    /**
     * Env var exported into each agent's TMUX session so callback scripts can identify
     * themselves to the harness server. Value format: `handshake.${UUID}`.
     *
     * Fail-fast: every callback script hard-fails when this env var is not set.
     */
    const val HANDSHAKE_GUID_ENV_VAR = "TICKET_SHEPHERD_HANDSHAKE_GUID"

    /**
     * Env var specifying the port for the embedded Ktor CIO HTTP server.
     * Read at server startup — fail hard if not set or not a valid port number.
     */
    const val SERVER_PORT_ENV_VAR = "TICKET_SHEPHERD_SERVER_PORT"
  }

  /**
   * Optional environment variables that control runtime behavior without being required.
   */
  object OPTIONAL_ENV_VARS {
    /**
     * When set to `"true"`, redirects spawned Claude Code agents to GLM (Z.AI) instead of
     * the real Anthropic API. Used by E2E tests that run the binary as a subprocess and need
     * GLM env vars exported into the tmux session.
     *
     * WHY an env var (not a CLI flag): The E2E test spawns the binary as a black box. An env
     * var is the simplest mechanism to influence behavior without changing the CLI interface.
     */
    const val TICKET_SHEPHERD_GLM_ENABLED = "TICKET_SHEPHERD_GLM_ENABLED"
  }

  /**
   * Required environment variables validated at harness initialization.
   *
   * See doc/core/git.md (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E) for specification.
   * All must be present at startup — fail hard immediately if any is missing.
   *
   * Validated by [com.glassthought.shepherd.core.initializer.EnvironmentValidator] at the
   * very start of `main()`, before any infrastructure is created.
   */
  object REQUIRED_ENV_VARS {
    /** Identifies the human operator in commit author attribution.
     *  Format: short username (e.g., `nickolaykondratyev`). */
    const val HOST_USERNAME = "HOST_USERNAME"

    /** Directory containing agent role definition `.md` files.
     *  Must point to `_config/agents/_generated/`. See ref.ap.Q7kR9vXm3pNwLfYtJ8dZs.E. */
    const val TICKET_SHEPHERD_AGENTS_DIR = "TICKET_SHEPHERD_AGENTS_DIR"

    /** Root directory for environment-specific configuration.
     *  System prompt files resolved relative to this path.
     *  See [SpawnTmuxAgentSessionUseCase — System Prompt File Resolution]. */
    const val MY_ENV = "MY_ENV"

    /** Model identifier for the ZAI (Z.AI / GLM) fast model.
     *  Used by [com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner]
     *  when spawning non-interactive agent subprocesses via PI CLI. */
    const val AI_MODEL_ZAI_FAST = "AI_MODEL__ZAI__FAST"

    /** All required environment variables. Used by [com.glassthought.shepherd.core.initializer.EnvironmentValidator]
     *  for startup validation. */
    val ALL: List<String> = listOf(
      HOST_USERNAME,
      TICKET_SHEPHERD_AGENTS_DIR,
      MY_ENV,
      AI_MODEL_ZAI_FAST,
    )
  }
}
