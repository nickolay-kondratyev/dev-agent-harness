package com.glassthought.shepherd.usecase.planning

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.facade.FakeAgentFacade
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.context.AgentInstructionRequest
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.executor.PartExecutorDeps
import com.glassthought.shepherd.core.executor.PartExecutorImpl
import com.glassthought.shepherd.core.executor.SubPartConfigBuilder
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.state.IterationConfig
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCase
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

// ── Test Fakes ─────────────────────────────────────────────────────────

private class StubContextForAgentProvider : ContextForAgentProvider {
    override suspend fun assembleInstructions(request: AgentInstructionRequest): Path {
        error("StubContextForAgentProvider: not expected to be called in this test")
    }
}

private val stubGitCommitStrategy = GitCommitStrategy { _: SubPartDoneContext ->
    error("StubGitCommitStrategy: not expected to be called in this test")
}

private val stubFailedToConvergeUseCase = FailedToConvergeUseCase { _, _ ->
    error("StubFailedToConvergeUseCase: not expected to be called in this test")
}

// ── Helpers ────────────────────────────────────────────────────────────

private fun createRoleDefinition(name: String, tempDir: Path): RoleDefinition {
    val filePath = tempDir.resolve("$name.md")
    Files.writeString(filePath, "# $name\nTest role definition")
    return RoleDefinition(
        name = name,
        description = "Test role: $name",
        descriptionLong = null,
        filePath = filePath,
    )
}

private fun buildPlanningPartDoerOnly(doerRole: String) = Part(
    name = "planning",
    phase = Phase.PLANNING,
    description = "planning part",
    subParts = listOf(
        SubPart(name = "planner", role = doerRole, agentType = "CLAUDE_CODE", model = "opus"),
    ),
)

private fun buildPlanningPartWithReviewer(doerRole: String, reviewerRole: String) = Part(
    name = "planning",
    phase = Phase.PLANNING,
    description = "planning part",
    subParts = listOf(
        SubPart(name = "planner", role = doerRole, agentType = "CLAUDE_CODE", model = "opus"),
        SubPart(
            name = "plan_reviewer", role = reviewerRole, agentType = "CLAUDE_CODE", model = "sonnet",
            iteration = IterationConfig(max = 2, current = 0),
        ),
    ),
)

private fun buildDeps(): PartExecutorDeps = PartExecutorDeps(
    agentFacade = FakeAgentFacade(),
    contextForAgentProvider = StubContextForAgentProvider(),
    gitCommitStrategy = stubGitCommitStrategy,
    failedToConvergeUseCase = stubFailedToConvergeUseCase,
    outFactory = com.asgard.core.out.impl.NoOpOutFactory(),
)

private fun buildFactory(
    planningPart: Part,
    roleDefinitions: Map<String, RoleDefinition>,
    aiOutputStructure: AiOutputStructure,
): ProductionPlanningPartExecutorFactory {
    val subPartConfigBuilder = SubPartConfigBuilder(
        aiOutputStructure = aiOutputStructure,
        roleDefinitions = roleDefinitions,
        ticketContent = "Test ticket content",
        planMdPath = null,
    )
    return ProductionPlanningPartExecutorFactory(
        planningPart = planningPart,
        deps = buildDeps(),
        subPartConfigBuilder = subPartConfigBuilder,
    )
}

// ── Tests ──────────────────────────────────────────────────────────────

class ProductionPlanningPartExecutorFactoryTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN a doer-only planning part") {
            val tempDir = Files.createTempDirectory("planning-factory-test")
            val doerRole = createRoleDefinition("PLANNER", tempDir)
            val roleDefinitions = mapOf("PLANNER" to doerRole)
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val planningPart = buildPlanningPartDoerOnly("PLANNER")
            val factory = buildFactory(planningPart, roleDefinitions, aiOutputStructure)

            describe("WHEN create() is called with empty priorConversionErrors") {
                val executor = factory.create(emptyList())

                it("THEN returns a PartExecutorImpl") {
                    executor.shouldBeInstanceOf<PartExecutorImpl>()
                }
            }

            describe("WHEN create() is called with non-empty priorConversionErrors") {
                val executor = factory.create(listOf("schema validation failed"))

                it("THEN still returns a PartExecutorImpl") {
                    executor.shouldBeInstanceOf<PartExecutorImpl>()
                }
            }
        }

        describe("GIVEN a doer+reviewer planning part") {
            val tempDir = Files.createTempDirectory("planning-factory-test-review")
            val doerRole = createRoleDefinition("PLANNER", tempDir)
            val reviewerRole = createRoleDefinition("PLAN_REVIEWER", tempDir)
            val roleDefinitions = mapOf("PLANNER" to doerRole, "PLAN_REVIEWER" to reviewerRole)
            val aiOutputStructure = AiOutputStructure(repoRoot = tempDir, branch = "test-branch")
            val planningPart = buildPlanningPartWithReviewer("PLANNER", "PLAN_REVIEWER")
            val factory = buildFactory(planningPart, roleDefinitions, aiOutputStructure)

            describe("WHEN create() is called") {
                val executor = factory.create(emptyList())

                it("THEN returns a PartExecutorImpl") {
                    executor.shouldBeInstanceOf<PartExecutorImpl>()
                }
            }
        }
    },
)
