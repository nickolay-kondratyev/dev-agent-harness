package com.glassthought.shepherd.core.executor

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.context.ExecutionContext
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.state.IterationConfig
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.state.SubPartRole
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

// ── Fixtures ───────────────────────────────────────────────────────────

private val TEST_REPO_ROOT: Path = Files.createTempDirectory("factory-creator-test")
private const val TEST_BRANCH = "test-branch"
private val TEST_AI_OUTPUT = AiOutputStructure(TEST_REPO_ROOT, TEST_BRANCH)

private val DOER_ROLE = RoleDefinition(
    name = "IMPL", description = "Implements", descriptionLong = null,
    filePath = Path.of("/roles/IMPL.md"),
)

private val REVIEWER_ROLE = RoleDefinition(
    name = "REVIEWER", description = "Reviews", descriptionLong = null,
    filePath = Path.of("/roles/REVIEWER.md"),
)

private val ROLES = mapOf("IMPL" to DOER_ROLE, "REVIEWER" to REVIEWER_ROLE)

private val DOER_ONLY_PART = Part(
    name = "part_a",
    phase = Phase.EXECUTION,
    description = "Part A",
    subParts = listOf(
        SubPart(name = "doer", role = "IMPL", agentType = "CLAUDE_CODE", model = "sonnet"),
    ),
)

private val DOER_REVIEWER_PART = Part(
    name = "part_b",
    phase = Phase.EXECUTION,
    description = "Part B with review",
    subParts = listOf(
        SubPart(name = "doer", role = "IMPL", agentType = "CLAUDE_CODE", model = "sonnet"),
        SubPart(
            name = "reviewer", role = "REVIEWER", agentType = "CLAUDE_CODE", model = "opus",
            iteration = IterationConfig(max = 5, current = 0),
        ),
    ),
)

private fun testConfigBuilder(): SubPartConfigBuilder = SubPartConfigBuilder(
    aiOutputStructure = TEST_AI_OUTPUT,
    roleDefinitions = ROLES,
    ticketContent = "Test ticket",
    planMdPath = null,
)

// ── Tests ──────────────────────────────────────────────────────────────

class PartExecutorFactoryCreatorTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        fun createTestDeps(): PartExecutorDeps {
            val instructionFile = Files.createTempFile("test-instructions", ".md")
            Files.writeString(instructionFile, "# Test instructions")

            return PartExecutorDeps(
                agentFacade = com.glassthought.shepherd.core.agent.facade.FakeAgentFacade(),
                contextForAgentProvider =
                    com.glassthought.shepherd.core.context.ContextForAgentProvider { _ ->
                        instructionFile
                    },
                gitCommitStrategy =
                    com.glassthought.shepherd.core.supporting.git.GitCommitStrategy { _ -> },
                failedToConvergeUseCase =
                    com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCase {
                        _, _ -> false
                    },
                outFactory = outFactory,
            )
        }

        describe("GIVEN resolveIterationConfig") {

            describe("WHEN part has a reviewer with iteration config") {
                val config = PartExecutorFactoryCreator.resolveIterationConfig(DOER_REVIEWER_PART)

                it("THEN returns the reviewer's iteration config") {
                    config shouldBe IterationConfig(max = 5, current = 0)
                }
            }

            describe("WHEN part is doer-only (no reviewer)") {
                val config = PartExecutorFactoryCreator.resolveIterationConfig(DOER_ONLY_PART)

                it("THEN returns default iteration config with max=1") {
                    config shouldBe IterationConfig(max = 1, current = 0)
                }
            }
        }

        describe("GIVEN buildFactory with a doer-only part") {
            val configBuilder = testConfigBuilder()
            val deps = createTestDeps()
            val factory = PartExecutorFactoryCreator.buildFactory(deps, configBuilder)

            describe("WHEN creating a PartExecutor") {
                val executor = factory.create(DOER_ONLY_PART)

                it("THEN returns a PartExecutorImpl") {
                    executor.shouldBeInstanceOf<PartExecutorImpl>()
                }
            }
        }

        describe("GIVEN buildFactory with a doer+reviewer part") {
            val configBuilder = testConfigBuilder()
            val deps = createTestDeps()
            val factory = PartExecutorFactoryCreator.buildFactory(deps, configBuilder)

            describe("WHEN creating a PartExecutor") {
                val executor = factory.create(DOER_REVIEWER_PART)

                it("THEN returns a PartExecutorImpl") {
                    executor.shouldBeInstanceOf<PartExecutorImpl>()
                }
            }
        }
    },
)
