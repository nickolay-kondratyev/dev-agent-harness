package com.glassthought.shepherd.core.supporting.git

import com.glassthought.shepherd.core.data.AgentType

/**
 * Stateless utility for building git commit author names.
 *
 * Format: `${CODING_AGENT}_${CODING_MODEL}_WITH-${HOST_USERNAME}`
 *
 * See doc/core/git.md — "Commit Author" (ref.ap.BvNCIzjdHS2iAP4gAQZQf.E).
 */
object CommitAuthorBuilder {

    private const val WITH_SEPARATOR = "_WITH-"

    /**
     * Builds a commit author name from the given parameters.
     *
     * @param agentType the agent type enum (determines short code)
     * @param model model name (e.g., "sonnet", "opus", "glm-5")
     * @param hostUsername from HOST_USERNAME env var
     * @return formatted author name (e.g., "CC_sonnet_WITH-nickolaykondratyev")
     * @throws IllegalArgumentException if inputs are invalid
     */
    fun build(
        agentType: AgentType,
        model: String,
        hostUsername: String,
    ): String {
        require(model.isNotBlank()) { "model must not be blank" }
        require(hostUsername.isNotBlank()) { "hostUsername must not be blank" }

        val agentCode = agentShortCode(agentType)
        return "${agentCode}_${model}${WITH_SEPARATOR}${hostUsername}"
    }

    /**
     * Maps [AgentType] to its short code for commit author attribution.
     *
     * No `else` branch — compiler enforces exhaustiveness on the enum.
     */
    private fun agentShortCode(agentType: AgentType): String =
        when (agentType) {
            AgentType.CLAUDE_CODE -> "CC"
            AgentType.PI -> "PI"
        }
}
