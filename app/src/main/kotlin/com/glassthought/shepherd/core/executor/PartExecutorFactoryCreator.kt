package com.glassthought.shepherd.core.executor

import com.asgard.core.out.OutFactory
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.state.IterationConfig
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import java.nio.file.Path

/**
 * Ticket-scoped inputs needed to create a [PartExecutorFactory].
 *
 * Groups the ticket-scoped dependencies that become available inside `wireTicketShepherd()`
 * and are needed to construct production [PartExecutorImpl] instances.
 */
data class PartExecutorFactoryContext(
    val shepherdContext: ShepherdContext,
    val outFactory: OutFactory,
    val aiOutputStructure: AiOutputStructure,
    val ticketData: TicketData,
    val planMdPath: Path?,
    val repoRoot: Path,
)

/**
 * Factory-of-factory: creates a ticket-scoped [PartExecutorFactory] from ticket-scoped inputs.
 *
 * Follows the same pattern as `SetupPlanUseCaseFactory` and `AllSessionsKillerFactory` —
 * the [TicketShepherdCreatorImpl][com.glassthought.shepherd.core.creator.TicketShepherdCreatorImpl]
 * receives this factory at construction time and calls [create] inside `wireTicketShepherd()`
 * once ticket-scoped dependencies are available.
 *
 * Test code substitutes a simple lambda that ignores the context and returns a controlled factory.
 */
fun interface PartExecutorFactoryCreator {

    /**
     * Creates a [PartExecutorFactory] for a specific ticket run.
     *
     * The production implementation constructs [AgentFacadeImpl], [ContextForAgentProvider],
     * [GitCommitStrategy], loads role definitions, and wires all [PartExecutorDeps] internally.
     *
     * @param context Ticket-scoped inputs (ShepherdContext, AiOutputStructure, TicketData, etc.)
     */
    suspend fun create(context: PartExecutorFactoryContext): PartExecutorFactory

    companion object {
        private val DEFAULT_ITERATION_CONFIG = IterationConfig(max = 1, current = 0)

        /**
         * Resolves [IterationConfig] from the part's reviewer sub-part (if present),
         * falling back to a sensible default for doer-only parts.
         */
        fun resolveIterationConfig(part: Part): IterationConfig {
            val reviewerSubPart = part.subParts.getOrNull(SubPartRole.REVIEWER_INDEX)
            return reviewerSubPart?.iteration ?: DEFAULT_ITERATION_CONFIG
        }

        /**
         * Builds a [PartExecutorFactory] from pre-resolved [PartExecutorDeps] and
         * [SubPartConfigBuilder]. Used by the production wiring in
         * [ProductionPartExecutorFactoryCreator].
         */
        fun buildFactory(
            deps: PartExecutorDeps,
            configBuilder: SubPartConfigBuilder,
        ): PartExecutorFactory {
            val priorPublicMdPaths = mutableListOf<Path>()

            return PartExecutorFactory { part: Part ->
                val doerConfig = configBuilder.build(
                    part = part,
                    subPartIndex = SubPartRole.DOER_INDEX,
                    priorPublicMdPaths = priorPublicMdPaths.toList(),
                )

                val reviewerConfig = if (part.subParts.size > 1) {
                    configBuilder.build(
                        part = part,
                        subPartIndex = SubPartRole.REVIEWER_INDEX,
                        priorPublicMdPaths = priorPublicMdPaths.toList(),
                    )
                } else {
                    null
                }

                // Register doer's public MD path for subsequent parts.
                priorPublicMdPaths.add(doerConfig.publicMdOutputPath)

                PartExecutorImpl(
                    doerConfig = doerConfig,
                    reviewerConfig = reviewerConfig,
                    deps = deps,
                    iterationConfig = resolveIterationConfig(part),
                )
            }
        }
    }
}
