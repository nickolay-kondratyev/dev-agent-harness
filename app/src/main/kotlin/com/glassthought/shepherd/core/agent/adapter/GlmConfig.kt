package com.glassthought.shepherd.core.agent.adapter

import com.asgard.core.annotation.AnchorPoint

/**
 * Configuration for redirecting Claude Code to GLM (Z.AI) via Anthropic-compatible env vars.
 *
 * When provided to [ClaudeCodeAdapter], the GLM env var exports are prepended to the
 * `innerCommand` in [ClaudeCodeAdapter.buildStartCommand], causing the spawned `claude` CLI
 * to transparently use GLM instead of the real Anthropic API.
 *
 * Required for integration tests that spawn real Claude Code agents — avoids consuming
 * Anthropic quota and uses the existing GLM subscription.
 *
 * See `ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md` for rationale.
 *
 * @param baseUrl Anthropic-compatible API endpoint (e.g., `https://api.z.ai/api/anthropic`).
 * @param authToken API token for the GLM endpoint (value of `Z_AI_GLM_API_TOKEN`).
 * @param defaultOpusModel GLM model name mapped to Opus (e.g., `glm-5`).
 * @param defaultSonnetModel GLM model name mapped to Sonnet (e.g., `glm-5`).
 * @param defaultHaikuModel GLM model name mapped to Haiku (e.g., `glm-4-flash`).
 *
 * ap.8BYTb6vcyAzpWavQguBrb.E
 */
@AnchorPoint("ap.8BYTb6vcyAzpWavQguBrb.E")
data class GlmConfig(
    val baseUrl: String,
    val authToken: String,
    val defaultOpusModel: String,
    val defaultSonnetModel: String,
    val defaultHaikuModel: String,
) {
    /**
     * Builds the env var export string to prepend to the shell command.
     *
     * Each var is exported as `export KEY=VALUE && `. The trailing `&& ` allows
     * concatenation with the rest of the inner command.
     */
    fun toEnvVarExports(): String = listOf(
        "export ANTHROPIC_BASE_URL=\"$baseUrl\"",
        "export ANTHROPIC_AUTH_TOKEN=\"$authToken\"",
        "export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1",
        "export ANTHROPIC_DEFAULT_OPUS_MODEL=\"$defaultOpusModel\"",
        "export ANTHROPIC_DEFAULT_SONNET_MODEL=\"$defaultSonnetModel\"",
        "export ANTHROPIC_DEFAULT_HAIKU_MODEL=\"$defaultHaikuModel\"",
    ).joinToString(" && ")

    companion object {
        /** Standard GLM config using Z.AI endpoint with default model mappings. */
        fun standard(authToken: String): GlmConfig = GlmConfig(
            baseUrl = "https://api.z.ai/api/anthropic",
            authToken = authToken,
            defaultOpusModel = "glm-5",
            defaultSonnetModel = "glm-5",
            defaultHaikuModel = "glm-4-flash",
        )
    }
}
