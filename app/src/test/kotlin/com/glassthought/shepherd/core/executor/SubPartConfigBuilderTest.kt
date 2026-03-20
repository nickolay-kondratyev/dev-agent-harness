package com.glassthought.shepherd.core.executor

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.state.SubPartRole
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

// ── Test fixtures ──────────────────────────────────────────────────────

private val TEST_REPO_ROOT: Path = Files.createTempDirectory("config-builder-test")
private const val TEST_BRANCH = "test-branch"
private val TEST_AI_OUTPUT_STRUCTURE = AiOutputStructure(TEST_REPO_ROOT, TEST_BRANCH)

private val DOER_ROLE_DEFINITION = RoleDefinition(
    name = "IMPLEMENTATION_WITH_SELF_PLAN",
    description = "Implements features",
    descriptionLong = null,
    filePath = Path.of("/config/roles/IMPLEMENTATION_WITH_SELF_PLAN.md"),
)

private val REVIEWER_ROLE_DEFINITION = RoleDefinition(
    name = "CODE_REVIEWER",
    description = "Reviews code",
    descriptionLong = null,
    filePath = Path.of("/config/roles/CODE_REVIEWER.md"),
)

private val ROLE_DEFINITIONS = mapOf(
    "IMPLEMENTATION_WITH_SELF_PLAN" to DOER_ROLE_DEFINITION,
    "CODE_REVIEWER" to REVIEWER_ROLE_DEFINITION,
)

private const val TICKET_CONTENT = "Implement the widget feature"
private val PLAN_MD_PATH: Path = TEST_AI_OUTPUT_STRUCTURE.planMd()

private val DOER_ONLY_PART = Part(
    name = "main",
    phase = Phase.EXECUTION,
    description = "Main implementation",
    subParts = listOf(
        SubPart(
            name = "impl",
            role = "IMPLEMENTATION_WITH_SELF_PLAN",
            agentType = "CLAUDE_CODE",
            model = "sonnet",
        ),
    ),
)

private val DOER_REVIEWER_PART = Part(
    name = "ui_design",
    phase = Phase.EXECUTION,
    description = "UI design with review",
    subParts = listOf(
        SubPart(
            name = "impl",
            role = "IMPLEMENTATION_WITH_SELF_PLAN",
            agentType = "CLAUDE_CODE",
            model = "sonnet",
        ),
        SubPart(
            name = "review",
            role = "CODE_REVIEWER",
            agentType = "CLAUDE_CODE",
            model = "opus",
        ),
    ),
)

private val PLANNING_PART = Part(
    name = "planning",
    phase = Phase.PLANNING,
    description = "Planning phase",
    subParts = listOf(
        SubPart(
            name = "planner",
            role = "IMPLEMENTATION_WITH_SELF_PLAN",
            agentType = "CLAUDE_CODE",
            model = "opus",
        ),
    ),
)

private fun createBuilder(
    planMdPath: Path? = PLAN_MD_PATH,
    roleDefinitions: Map<String, RoleDefinition> = ROLE_DEFINITIONS,
): SubPartConfigBuilder = SubPartConfigBuilder(
    aiOutputStructure = TEST_AI_OUTPUT_STRUCTURE,
    roleDefinitions = roleDefinitions,
    ticketContent = TICKET_CONTENT,
    planMdPath = planMdPath,
)

// ── Tests ──────────────────────────────────────────────────────────────

class SubPartConfigBuilderTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        // ── Doer-only part (execution phase) ──

        describe("GIVEN a doer-only execution part") {
            val builder = createBuilder()

            describe("WHEN building config for the doer (index 0)") {
                val config = builder.build(
                    part = DOER_ONLY_PART,
                    subPartIndex = 0,
                    priorPublicMdPaths = emptyList(),
                )

                it("THEN partName matches the part name") {
                    config.partName shouldBe "main"
                }

                it("THEN subPartName matches the sub-part name") {
                    config.subPartName shouldBe "impl"
                }

                it("THEN subPartIndex is 0") {
                    config.subPartIndex shouldBe 0
                }

                it("THEN subPartRole is DOER") {
                    config.subPartRole shouldBe SubPartRole.DOER
                }

                it("THEN agentType is CLAUDE_CODE") {
                    config.agentType shouldBe AgentType.CLAUDE_CODE
                }

                it("THEN model is sonnet") {
                    config.model shouldBe "sonnet"
                }

                it("THEN systemPromptPath is the role definition file path") {
                    config.systemPromptPath shouldBe DOER_ROLE_DEFINITION.filePath
                }

                it("THEN bootstrapMessage is set") {
                    config.bootstrapMessage shouldBe "Waiting for instructions."
                }

                it("THEN roleDefinition matches the resolved role") {
                    config.roleDefinition shouldBe DOER_ROLE_DEFINITION
                }

                it("THEN ticketContent matches") {
                    config.ticketContent shouldBe TICKET_CONTENT
                }

                it("THEN outputDir points to execution comm out dir") {
                    config.outputDir shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.executionCommOutDir("main", "impl")
                }

                it("THEN publicMdOutputPath points to execution public MD") {
                    config.publicMdOutputPath shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.executionPublicMd("main", "impl")
                }

                it("THEN privateMdPath points to execution private MD") {
                    config.privateMdPath shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.executionPrivateMd("main", "impl")
                }

                it("THEN executionContext has correct partName") {
                    config.executionContext.partName shouldBe "main"
                }

                it("THEN executionContext has correct partDescription") {
                    config.executionContext.partDescription shouldBe "Main implementation"
                }

                it("THEN executionContext has planMdPath set") {
                    config.executionContext.planMdPath shouldBe PLAN_MD_PATH
                }

                it("THEN executionContext has empty priorPublicMdPaths") {
                    config.executionContext.priorPublicMdPaths shouldBe emptyList()
                }

                it("THEN doerPublicMdPath is null (doer has no doer reference)") {
                    config.doerPublicMdPath shouldBe null
                }

                it("THEN feedbackDir is null (doer has no feedback)") {
                    config.feedbackDir shouldBe null
                }
            }
        }

        // ── Doer+Reviewer part ──

        describe("GIVEN a doer+reviewer execution part") {
            val builder = createBuilder()

            describe("WHEN building config for the reviewer (index 1)") {
                val config = builder.build(
                    part = DOER_REVIEWER_PART,
                    subPartIndex = 1,
                    priorPublicMdPaths = emptyList(),
                )

                it("THEN subPartRole is REVIEWER") {
                    config.subPartRole shouldBe SubPartRole.REVIEWER
                }

                it("THEN model is opus") {
                    config.model shouldBe "opus"
                }

                it("THEN roleDefinition is CODE_REVIEWER") {
                    config.roleDefinition shouldBe REVIEWER_ROLE_DEFINITION
                }

                it("THEN doerPublicMdPath references the doer's public MD") {
                    config.doerPublicMdPath shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.executionPublicMd("ui_design", "impl")
                }

                it("THEN feedbackDir references the part's feedback dir") {
                    config.feedbackDir shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.feedbackDir("ui_design")
                }
            }
        }

        // ── Planning phase ──

        describe("GIVEN a planning phase part") {
            val builder = createBuilder()

            describe("WHEN building config for the planner (index 0)") {
                val config = builder.build(
                    part = PLANNING_PART,
                    subPartIndex = 0,
                    priorPublicMdPaths = emptyList(),
                )

                it("THEN publicMdOutputPath uses planning path resolution") {
                    config.publicMdOutputPath shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.planningPublicMd("planner")
                }

                it("THEN privateMdPath uses planning path resolution") {
                    config.privateMdPath shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.planningPrivateMd("planner")
                }

                it("THEN outputDir uses planning comm out dir") {
                    config.outputDir shouldBe
                        TEST_AI_OUTPUT_STRUCTURE.planningCommOutDir("planner")
                }
            }
        }

        // ── Prior public MD paths ──

        describe("GIVEN prior parts have completed") {
            val builder = createBuilder()
            val priorPaths = listOf(
                Path.of("/prior/part1/PUBLIC.md"),
                Path.of("/prior/part2/PUBLIC.md"),
            )

            describe("WHEN building config with prior public MD paths") {
                val config = builder.build(
                    part = DOER_ONLY_PART,
                    subPartIndex = 0,
                    priorPublicMdPaths = priorPaths,
                )

                it("THEN executionContext contains the prior paths") {
                    config.executionContext.priorPublicMdPaths shouldBe priorPaths
                }
            }
        }

        // ── No plan MD path ──

        describe("GIVEN no planning phase (planMdPath is null)") {
            val builder = createBuilder(planMdPath = null)

            describe("WHEN building config") {
                val config = builder.build(
                    part = DOER_ONLY_PART,
                    subPartIndex = 0,
                    priorPublicMdPaths = emptyList(),
                )

                it("THEN executionContext.planMdPath is null") {
                    config.executionContext.planMdPath shouldBe null
                }
            }
        }

        // ── Unknown role ──

        describe("GIVEN a part with an unknown role") {
            val builder = createBuilder()
            val unknownRolePart = DOER_ONLY_PART.copy(
                subParts = listOf(
                    SubPart(
                        name = "impl",
                        role = "NONEXISTENT_ROLE",
                        agentType = "CLAUDE_CODE",
                        model = "sonnet",
                    ),
                ),
            )

            describe("WHEN building config") {
                it("THEN throws IllegalArgumentException mentioning the unknown role") {
                    val ex = shouldThrow<IllegalArgumentException> {
                        builder.build(
                            part = unknownRolePart,
                            subPartIndex = 0,
                            priorPublicMdPaths = emptyList(),
                        )
                    }
                    ex.message shouldContain "NONEXISTENT_ROLE"
                }
            }
        }

        // ── Invalid agent type ──

        describe("GIVEN a part with an invalid agent type") {
            val builder = createBuilder()
            val invalidAgentTypePart = DOER_ONLY_PART.copy(
                subParts = listOf(
                    SubPart(
                        name = "impl",
                        role = "IMPLEMENTATION_WITH_SELF_PLAN",
                        agentType = "INVALID_AGENT",
                        model = "sonnet",
                    ),
                ),
            )

            describe("WHEN building config") {
                it("THEN throws IllegalArgumentException mentioning the invalid type") {
                    val ex = shouldThrow<IllegalArgumentException> {
                        builder.build(
                            part = invalidAgentTypePart,
                            subPartIndex = 0,
                            priorPublicMdPaths = emptyList(),
                        )
                    }
                    ex.message shouldContain "INVALID_AGENT"
                }
            }
        }

        // ── Agent type case insensitivity ──

        describe("GIVEN a part with lowercase agent type 'claude_code'") {
            val builder = createBuilder()
            val lowerCasePart = DOER_ONLY_PART.copy(
                subParts = listOf(
                    SubPart(
                        name = "impl",
                        role = "IMPLEMENTATION_WITH_SELF_PLAN",
                        agentType = "claude_code",
                        model = "sonnet",
                    ),
                ),
            )

            describe("WHEN building config") {
                val config = builder.build(
                    part = lowerCasePart,
                    subPartIndex = 0,
                    priorPublicMdPaths = emptyList(),
                )

                it("THEN agentType is correctly parsed as CLAUDE_CODE") {
                    config.agentType shouldBe AgentType.CLAUDE_CODE
                }
            }
        }
    },
)
