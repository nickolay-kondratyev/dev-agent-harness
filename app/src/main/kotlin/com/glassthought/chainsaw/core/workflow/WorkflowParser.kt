package com.glassthought.chainsaw.core.workflow

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Parses workflow definition JSON files into [WorkflowDefinition].
 *
 * See design doc: ref.ap.Wya4gZPW6RPpJHdtoJqZO.E (Workflow Definition — Kotlin + JSON)
 *
 * ap.U5oDohccLN3tugPzK9TJa.E
 */
interface WorkflowParser {

    /**
     * Parses the workflow JSON file at [path] and returns a [WorkflowDefinition].
     *
     * @throws NoSuchFileException if [path] does not exist.
     * @throws com.fasterxml.jackson.core.JsonProcessingException if the file contains malformed JSON
     *   or is missing required fields.
     * @throws IllegalArgumentException if structural validation fails (e.g., blank name,
     *   neither parts nor planning fields present, empty phases).
     */
    suspend fun parse(path: Path): WorkflowDefinition

    companion object {
        fun standard(outFactory: OutFactory): WorkflowParser = WorkflowParserImpl(outFactory)
    }
}

/**
 * Default implementation of [WorkflowParser].
 *
 * Reads the JSON file from disk (on IO dispatcher), deserializes it with Jackson,
 * and performs post-deserialization structural validation.
 */
class WorkflowParserImpl(outFactory: OutFactory) : WorkflowParser {

    private val out = outFactory.getOutForClass(WorkflowParserImpl::class)

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)

    override suspend fun parse(path: Path): WorkflowDefinition {
        out.debug("reading_workflow_definition") {
            listOf(Val(path.toString(), ValType.FILE_PATH_STRING))
        }

        val content = withContext(Dispatchers.IO) { path.readText() }

        val definition = objectMapper.readValue<WorkflowDefinition>(content)

        validate(definition, path)

        out.info(
            "workflow_parsed",
            Val(definition.name, ValType.STRING_USER_AGNOSTIC),
        )

        return definition
    }

    private fun validate(definition: WorkflowDefinition, path: Path) {
        require(definition.name.isNotBlank()) {
            "Workflow name must not be blank in: $path"
        }

        val hasParts = definition.parts != null
        val hasPlanning = definition.planningPhases != null

        require(hasParts || hasPlanning) {
            "Workflow must have either 'parts' (straightforward) or 'planningPhases' (with-planning), but neither was found in: $path"
        }

        require(!(hasParts && hasPlanning)) {
            "Workflow must have either 'parts' or 'planningPhases', but not both. Found both in: $path"
        }

        if (hasParts) {
            require(definition.parts!!.isNotEmpty()) {
                "Workflow 'parts' list must not be empty in: $path"
            }

            definition.parts.forEachIndexed { partIndex, part ->
                require(part.name.isNotBlank()) {
                    "Part at index $partIndex has blank name in: $path"
                }

                require(part.iteration.max > 0) {
                    "Part '${part.name}' iteration max must be positive, but was ${part.iteration.max} in: $path"
                }

                require(part.phases.isNotEmpty()) {
                    "Part '${part.name}' at index $partIndex has empty phases list in: $path"
                }

                part.phases.forEachIndexed { phaseIndex, phase ->
                    require(phase.role.isNotBlank()) {
                        "Phase at index $phaseIndex in part '${part.name}' has blank role in: $path"
                    }
                }
            }
        }

        if (hasPlanning) {
            require(definition.planningPhases!!.isNotEmpty()) {
                "planningPhases must not be empty in: $path"
            }

            require(definition.planningIteration != null) {
                "planningIteration is required when planningPhases is present in: $path"
            }

            require(definition.planningIteration!!.max > 0) {
                "planningIteration max must be positive, but was ${definition.planningIteration!!.max} in: $path"
            }

            require(definition.executionPhasesFrom?.isNotBlank() == true) {
                "executionPhasesFrom is required and must not be blank when planningPhases is present in: $path"
            }

            definition.planningPhases.forEachIndexed { phaseIndex, phase ->
                require(phase.role.isNotBlank()) {
                    "Planning phase at index $phaseIndex has blank role in: $path"
                }
            }
        }
    }
}
