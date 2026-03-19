package com.glassthought.shepherd.core.workflow

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.fasterxml.jackson.core.JacksonException
import com.glassthought.shepherd.core.infra.DispatcherProvider
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.ShepherdObjectMapper
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Parses a workflow definition JSON file into a [WorkflowDefinition].
 *
 * ap.kz1nvt0wyd2UpWsCGmm7Y.E
 */
fun interface WorkflowParser {

    /**
     * Parses the workflow file at `config/workflows/<workflowName>.json`
     * relative to [workingDirectory] and returns a validated [WorkflowDefinition].
     *
     * @param workflowName The workflow name (e.g., "straightforward", "with-planning").
     * @param workingDirectory The project root directory containing `config/workflows/`.
     * @throws IllegalArgumentException if the file is missing, malformed, or fails validation.
     */
    suspend fun parse(workflowName: String, workingDirectory: Path): WorkflowDefinition

    companion object {
        fun standard(outFactory: OutFactory): WorkflowParser =
            WorkflowParserImpl(outFactory)
    }
}

/**
 * Default implementation of [WorkflowParser].
 *
 * Reads the JSON file from disk (on IO dispatcher), deserializes with [ShepherdObjectMapper],
 * and validates phase constraints:
 * - Straightforward parts must all have [Phase.EXECUTION].
 * - Planning parts must all have [Phase.PLANNING].
 */
class WorkflowParserImpl(
    outFactory: OutFactory,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
) : WorkflowParser {

    private val out = outFactory.getOutForClass(WorkflowParserImpl::class)
    private val mapper = ShepherdObjectMapper.create()

    override suspend fun parse(workflowName: String, workingDirectory: Path): WorkflowDefinition {
        out.debug("parsing_workflow") {
            listOf(Val(workflowName, ValType.STRING_USER_AGNOSTIC))
        }

        val filePath = workingDirectory.resolve("config/workflows/$workflowName.json")

        require(filePath.exists()) {
            "Workflow file not found: $filePath"
        }

        val content = withContext(dispatcherProvider.io()) { filePath.readText() }

        val definition = try {
            mapper.readValue(content, WorkflowDefinition::class.java)
        } catch (e: JacksonException) {
            throw IllegalArgumentException(
                "Failed to parse workflow file: $filePath — ${e.message}",
                e,
            )
        }

        validatePhases(definition, filePath)

        out.info(
            "workflow_parsed",
            Val(definition.name, ValType.STRING_USER_AGNOSTIC),
            Val(
                if (definition.isStraightforward) "straightforward" else "with-planning",
                ValType.STRING_USER_AGNOSTIC,
            ),
        )

        return definition
    }

    private fun validatePhases(definition: WorkflowDefinition, filePath: Path) {
        if (definition.isStraightforward) {
            val nonExecutionParts = definition.parts!!.filter { it.phase != Phase.EXECUTION }
            require(nonExecutionParts.isEmpty()) {
                "Straightforward workflow '$filePath' must have all parts with phase='execution', " +
                    "but found parts with wrong phase: ${nonExecutionParts.map { it.name }}"
            }
        }

        if (definition.isWithPlanning) {
            val nonPlanningParts = definition.planningParts!!.filter { it.phase != Phase.PLANNING }
            require(nonPlanningParts.isEmpty()) {
                "With-planning workflow '$filePath' must have all planningParts with phase='planning', " +
                    "but found parts with wrong phase: ${nonPlanningParts.map { it.name }}"
            }
        }
    }
}
