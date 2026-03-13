package com.glassthought.shepherd.core

object Constants {
  /** Claude Code CLI configuration. */
  object CLAUDE_CODE {
    /** Root directory where Claude Code stores JSONL session files.
     *  Used by [ClaudeCodeAgentSessionIdResolver] to scan for HandshakeGuid matches. */
    fun defaultProjectsDir(): java.nio.file.Path =
      java.nio.file.Path.of(System.getProperty("user.home"), ".claude", "projects")
  }

  /** Model identifiers as sent to the provider's API (wire format). */
  object DIRECT_LLM_API_MODEL_NAME {
    /** GLM highest-tier model identifier for the Z.AI Anthropic-compatible API. */
    const val GLM_HIGHEST_TIER = "glm-5"

    /** GLM quick/cheap tier model identifier for Z.AI Anthropic-compatible API. */
    const val GLM_QUICK_CHEAP = "glm-4.7-flash"
  }

  /** Z.AI API configuration constants. */
  object Z_AI_API {
    /** Anthropic-compatible messages endpoint for Z.AI GLM subscription.
     * Base URL is `https://api.z.ai/api/anthropic`; `/v1/messages` is the messages path
     * required by the Anthropic Messages API spec. */
    const val CHAT_COMPLETIONS_ENDPOINT = "https://api.z.ai/api/anthropic/v1/messages"
    const val API_TOKEN_ENV_VAR = "Z_AI_GLM_API_TOKEN"
    /** Default max tokens for API responses. Can be overridden via environment variable. */
    const val MAX_TOKENS_ENV_VAR = "Z_AI_GLM_MAX_TOKENS"
    const val DEFAULT_MAX_TOKENS = 4096
    /** Anthropic-compatible API version header value required by the Z.AI messages endpoint. */
    const val ANTHROPIC_API_VERSION = "2023-06-01"
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

    /** Directory containing one file per model, each holding the model version string.
     *  Used to build commit author: `CC_sonnet-v4.6_WITH-nickolaykondratyev`.
     *  See doc/core/git.md for file format and lookup rules. */
    const val MODEL_VERSION_DIR = "MODEL_VERSION_DIR"

    /** Directory containing agent role definition `.md` files.
     *  Must point to `_config/agents/_generated/`. See ref.ap.Q7kR9vXm3pNwLfYtJ8dZs.E. */
    const val TICKET_SHEPHERD_AGENTS_DIR = "TICKET_SHEPHERD_AGENTS_DIR"

    /** Root directory for environment-specific configuration.
     *  System prompt files resolved relative to this path.
     *  See [SpawnTmuxAgentSessionUseCase — System Prompt File Resolution]. */
    const val MY_ENV = "MY_ENV"

    /** All required environment variables. Used by [com.glassthought.shepherd.core.initializer.EnvironmentValidator]
     *  for startup validation. */
    val ALL: List<String> = listOf(
      HOST_USERNAME,
      MODEL_VERSION_DIR,
      TICKET_SHEPHERD_AGENTS_DIR,
      MY_ENV,
      Z_AI_API.API_TOKEN_ENV_VAR,
    )
  }

  /** Returns the resolved max tokens value — from [Z_AI_API.MAX_TOKENS_ENV_VAR] env var if set, otherwise [Z_AI_API.DEFAULT_MAX_TOKENS]. */
  fun resolveMaxTokens(): Int =
    System.getenv(Z_AI_API.MAX_TOKENS_ENV_VAR)?.toIntOrNull() ?: Z_AI_API.DEFAULT_MAX_TOKENS
}
