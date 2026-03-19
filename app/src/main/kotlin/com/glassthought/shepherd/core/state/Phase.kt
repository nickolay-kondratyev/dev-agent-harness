package com.glassthought.shepherd.core.state

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Top-level phase of a plan part.
 *
 * JSON serialization uses lowercase names ("planning", "execution").
 */
enum class Phase {
    @JsonProperty("planning") PLANNING,
    @JsonProperty("execution") EXECUTION,
}
