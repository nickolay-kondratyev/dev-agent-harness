package com.glassthought.shepherd.core.context

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.infra.DispatcherProvider
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Rendering engine that walks an [InstructionSection] plan list and produces the final
 * `instructions.md` file for an agent.
 *
 * The engine is intentionally separated from [ContextForAgentProvider] — the provider
 * selects the plan; the assembler renders it. This keeps each class focused on a single
 * responsibility.
 *
 * See ContextForAgentProvider spec (ref.ap.9HksYVzl1KkR9E1L2x8Tx.E), "assembleFromPlan"
 * section.
 *
 * ap.Xk7mPvR3nLwQ9tJsF2dYh.E
 */
class InstructionPlanAssembler(
    outFactory: OutFactory,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.standard(),
) {

    private val out = outFactory.getOutForClass(InstructionPlanAssembler::class)

    companion object {
        /** Markdown horizontal rule separator used to delimit instruction sections. */
        private const val SECTION_SEPARATOR = "\n\n---\n\n"
    }

    /**
     * Walks the [plan], renders each section against the [request], filters out nulls
     * (skipped sections), joins with a horizontal-rule separator, and writes the result
     * to `request.outputDir/instructions.md`.
     *
     * File I/O is dispatched on [DispatcherProvider.io].
     *
     * @return the path to the written `instructions.md` file.
     */
    suspend fun assembleFromPlan(
        plan: List<InstructionSection>,
        request: AgentInstructionRequest,
    ): Path = withContext(dispatcherProvider.io()) {
        val renderedSections = plan.mapNotNull { section -> section.render(request) }
        val content = renderedSections.joinToString(SECTION_SEPARATOR)

        Files.createDirectories(request.outputDir)
        val instructionsPath = request.outputDir.resolve("instructions.md")
        instructionsPath.toFile().writeText(content)

        out.info(
            "instructions_file_written",
            Val(instructionsPath.toString(), ValType.FILE_PATH_STRING),
        )

        instructionsPath
    }
}
