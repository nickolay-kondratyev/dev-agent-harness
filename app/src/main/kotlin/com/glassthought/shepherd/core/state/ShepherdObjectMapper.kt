package com.glassthought.shepherd.core.state

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Shared [ObjectMapper] factory for plan/current-state JSON serialization.
 *
 * Configuration:
 * - [KotlinModule] for Kotlin data class support (default values, nullable fields).
 * - [JsonInclude.Include.NON_NULL] to omit absent runtime fields in plan-flow JSON.
 * - [DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES] = false for forward compatibility.
 * - camelCase field naming (Jackson default — matches Kotlin property names).
 */
object ShepherdObjectMapper {

    fun create(): ObjectMapper {
        return ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
}
