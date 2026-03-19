package com.glassthought.shepherd.core.agent.contextwindow

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO matching the JSON schema of `context_window_slim.json` written by the external hook.
 *
 * Uses [JsonProperty] annotations because the JSON file uses snake_case while
 * Kotlin conventions (and [com.glassthought.shepherd.core.state.ShepherdObjectMapper]) use camelCase.
 */
data class ContextWindowSlimDto(
    @param:JsonProperty("file_updated_timestamp")
    val fileUpdatedTimestamp: String? = null,

    @param:JsonProperty("remaining_percentage")
    val remainingPercentage: Int? = null,
)
