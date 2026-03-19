package com.glassthought.shepherd.core.state

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.fasterxml.jackson.core.JacksonException
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.io.path.readText

/**
 * Converts plan_flow.json (produced by a planning agent) into execution parts
 * appended to the in-memory [CurrentState].
 *
 * Steps:
 * 1. Read and deserialize plan_flow.json
 * 2. Validate all parts have phase=EXECUTION
 * 3. Initialize runtime fields (status=NOT_STARTED, iteration.current=0)
 * 4. Append to CurrentState, ensure directory structure, flush, delete plan_flow.json
 *
 * @throws PlanConversionException on validation or parsing failures
 *
 * ap.bV7kMn3pQ9wRxYz2LfJ8s.E
 */
fun interface PlanFlowConverter {

    /**
     * Reads plan_flow.json, validates, initializes runtime fields, appends to [currentState],
     * ensures directory structure, flushes to disk, and deletes plan_flow.json.
     *
     * @return The list of initialized execution parts that were appended.
     * @throws PlanConversionException if plan_flow.json is malformed, has no parts,
     *         or contains non-execution phase parts.
     */
    suspend fun convertAndAppend(currentState: CurrentState): List<Part>
}

class PlanFlowConverterImpl(
    private val aiOutputStructure: AiOutputStructure,
    private val currentStatePersistence: CurrentStatePersistence,
    outFactory: OutFactory,
) : PlanFlowConverter {

    private val out = outFactory.getOutForClass(PlanFlowConverterImpl::class)
    private val mapper = ShepherdObjectMapper.create()

    override suspend fun convertAndAppend(currentState: CurrentState): List<Part> {
        val planFlowPath = aiOutputStructure.planFlowJson()

        // 1. Read and deserialize
        val rawContent = try {
            planFlowPath.readText()
        } catch (e: NoSuchFileException) {
            throw PlanConversionException(
                "plan_flow.json does not exist at: $planFlowPath",
                e,
            )
        }
        val parsedState = try {
            mapper.readValue(rawContent, CurrentState::class.java)
        } catch (e: JacksonException) {
            throw PlanConversionException(
                "Failed to parse plan_flow.json: ${e.message}",
                e,
            )
        }

        // 2. Validate: at least one part
        if (parsedState.parts.isEmpty()) {
            throw PlanConversionException(
                "plan_flow.json must contain at least one part, but parts array is empty"
            )
        }

        // 3. Validate: all parts must have phase=EXECUTION
        val nonExecutionParts = parsedState.parts.filter { it.phase != Phase.EXECUTION }
        if (nonExecutionParts.isNotEmpty()) {
            throw PlanConversionException(
                "All parts in plan_flow.json must have phase='execution', " +
                    "but found non-execution parts: ${nonExecutionParts.map { "${it.name}(${it.phase})" }}"
            )
        }

        // 4. Initialize runtime fields
        val initializedParts = parsedState.parts.map { part ->
            CurrentStateInitializerImpl.initializePart(part)
        }

        // 5. Append to in-memory CurrentState
        currentState.appendExecutionParts(initializedParts)

        // 6. Ensure directory structure for new parts
        aiOutputStructure.ensureStructure(initializedParts)

        // 7. Flush to disk
        currentStatePersistence.flush(currentState)

        out.info(
            "plan_flow_converted",
            Val(initializedParts.size, ValType.COUNT),
        )

        // 8. Delete plan_flow.json
        Files.delete(planFlowPath)

        // 9. Return initialized execution parts
        return initializedParts
    }
}
