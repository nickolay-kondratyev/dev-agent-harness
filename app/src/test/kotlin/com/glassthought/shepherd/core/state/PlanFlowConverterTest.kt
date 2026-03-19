package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.filestructure.AiOutputStructure
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Holds test infrastructure: temp directory, AiOutputStructure, persistence, and converter.
 */
data class PlanFlowTestContext(
    val tempDir: Path,
    val aiOutputStructure: AiOutputStructure,
    val persistence: CurrentStatePersistence,
    val converter: PlanFlowConverterImpl,
)

class PlanFlowConverterTest : AsgardDescribeSpec({

    fun createTestContext(): PlanFlowTestContext {
        val tempDir = Files.createTempDirectory("plan-flow-test")
        val branch = "test-branch"
        val aiOutputStructure = AiOutputStructure(tempDir, branch)
        val persistence = CurrentStatePersistenceImpl(aiOutputStructure)
        val converter = PlanFlowConverterImpl(aiOutputStructure, persistence, outFactory)
        Files.createDirectories(aiOutputStructure.harnessPrivateDir())
        return PlanFlowTestContext(tempDir, aiOutputStructure, persistence, converter)
    }

    // ── Valid plan_flow.json — parts structure ──

    describe("GIVEN a valid plan_flow.json with one execution part") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design the dashboard UI",
              "subParts": [
                { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" },
                { "name": "review", "role": "UI_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
                  "iteration": { "max": 3 } }
              ]
            }
          ]
        }
        """.trimIndent()

        describe("AND a CurrentState with an existing planning part") {

            val planningPart = Part(
                name = "planning",
                phase = Phase.PLANNING,
                description = "Plan the workflow",
                subParts = listOf(
                    SubPart(
                        name = "plan",
                        role = "PLANNER",
                        agentType = "ClaudeCode",
                        model = "opus",
                        status = SubPartStatus.COMPLETED,
                    ),
                ),
            )

            describe("WHEN convertAndAppend is called") {

                lateinit var ctx: PlanFlowTestContext
                lateinit var currentState: CurrentState
                lateinit var result: List<Part>

                beforeEach {
                    ctx = createTestContext()
                    ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                    currentState = CurrentState(parts = mutableListOf(planningPart))
                    result = ctx.converter.convertAndAppend(currentState)
                }

                it("THEN returns one execution part") {
                    result shouldHaveSize 1
                }

                it("THEN returned part name is ui_design") {
                    result[0].name shouldBe "ui_design"
                }

                it("THEN returned part phase is EXECUTION") {
                    result[0].phase shouldBe Phase.EXECUTION
                }

                it("THEN returned part has two subParts") {
                    result[0].subParts shouldHaveSize 2
                }

                it("THEN impl subPart status is NOT_STARTED") {
                    result[0].subParts[0].status shouldBe SubPartStatus.NOT_STARTED
                }

                it("THEN review subPart status is NOT_STARTED") {
                    result[0].subParts[1].status shouldBe SubPartStatus.NOT_STARTED
                }

                it("THEN review subPart iteration.current is 0") {
                    result[0].subParts[1].iteration!!.current shouldBe 0
                }

                it("THEN review subPart iteration.max is 3") {
                    result[0].subParts[1].iteration!!.max shouldBe 3
                }

                it("THEN currentState has two parts total (planning + execution appended)") {
                    currentState.parts shouldHaveSize 2
                }

                it("THEN currentState first part is the original planning part") {
                    currentState.parts[0].name shouldBe "planning"
                    currentState.parts[0].phase shouldBe Phase.PLANNING
                }

                it("THEN currentState second part is the appended execution part") {
                    currentState.parts[1].name shouldBe "ui_design"
                    currentState.parts[1].phase shouldBe Phase.EXECUTION
                }

                it("THEN plan_flow.json is deleted") {
                    ctx.aiOutputStructure.planFlowJson().exists() shouldBe false
                }

                it("THEN current_state.json is flushed to disk") {
                    ctx.aiOutputStructure.currentStateJson().exists() shouldBe true
                }
            }
        }
    }

    // ── Multiple execution parts ──

    describe("GIVEN a plan_flow.json with two execution parts") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design the UI",
              "subParts": [
                { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" }
              ]
            },
            {
              "name": "backend",
              "phase": "execution",
              "description": "Implement backend",
              "subParts": [
                { "name": "impl", "role": "BACKEND_DEV", "agentType": "ClaudeCode", "model": "opus" }
              ]
            }
          ]
        }
        """.trimIndent()

        describe("WHEN convertAndAppend is called") {

            it("THEN returns two execution parts") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                val result = ctx.converter.convertAndAppend(currentState)
                result shouldHaveSize 2
            }

            it("THEN first part name is ui_design") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                val result = ctx.converter.convertAndAppend(currentState)
                result[0].name shouldBe "ui_design"
            }

            it("THEN second part name is backend") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                val result = ctx.converter.convertAndAppend(currentState)
                result[1].name shouldBe "backend"
            }

            it("THEN all sub-parts have status NOT_STARTED") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                val result = ctx.converter.convertAndAppend(currentState)
                result.flatMap { it.subParts }.all { it.status == SubPartStatus.NOT_STARTED } shouldBe true
            }
        }
    }

    // ── plan_flow.json with runtime fields present (silently overwritten) ──

    describe("GIVEN a plan_flow.json with runtime fields already present") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design UI",
              "subParts": [
                {
                  "name": "impl",
                  "role": "UI_DESIGNER",
                  "agentType": "ClaudeCode",
                  "model": "sonnet",
                  "status": "IN_PROGRESS",
                  "sessionIds": [{"handshakeGuid": "hg1", "agentSession": {"id": "s1"}, "agentType": "ClaudeCode", "model": "sonnet", "timestamp": "2026-01-01T00:00:00Z"}]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        describe("WHEN convertAndAppend is called") {

            it("THEN does not throw") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                ctx.converter.convertAndAppend(currentState)
            }

            it("THEN status is re-initialized to NOT_STARTED") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                val result = ctx.converter.convertAndAppend(currentState)
                result[0].subParts[0].status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN sessionIds are cleared (null)") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                val result = ctx.converter.convertAndAppend(currentState)
                result[0].subParts[0].sessionIds shouldBe null
            }
        }
    }

    // ── Validation: plan_flow.json does not exist ──

    describe("GIVEN plan_flow.json does not exist") {

        describe("WHEN convertAndAppend is called") {

            it("THEN throws PlanConversionException mentioning plan_flow.json") {
                val ctx = createTestContext()
                // Do NOT write plan_flow.json — it should not exist
                val currentState = CurrentState(parts = mutableListOf())

                val ex = shouldThrow<PlanConversionException> {
                    ctx.converter.convertAndAppend(currentState)
                }
                ex.message shouldContain "plan_flow.json"
            }
        }
    }

    // ── Validation: empty parts array ──

    describe("GIVEN a plan_flow.json with empty parts array") {

        val planFlowJson = """{ "parts": [] }"""

        describe("WHEN convertAndAppend is called") {

            it("THEN throws PlanConversionException mentioning at least one part") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())

                val ex = shouldThrow<PlanConversionException> {
                    ctx.converter.convertAndAppend(currentState)
                }
                ex.message shouldContain "at least one part"
            }
        }
    }

    // ── Validation: non-execution phase ──

    describe("GIVEN a plan_flow.json containing a planning-phase part") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "planning",
              "phase": "planning",
              "description": "Should not be here",
              "subParts": [
                { "name": "plan", "role": "PLANNER", "agentType": "ClaudeCode", "model": "opus" }
              ]
            }
          ]
        }
        """.trimIndent()

        describe("WHEN convertAndAppend is called") {

            it("THEN throws PlanConversionException mentioning phase execution") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())

                val ex = shouldThrow<PlanConversionException> {
                    ctx.converter.convertAndAppend(currentState)
                }
                ex.message shouldContain "phase='execution'"
            }

            it("THEN exception message includes the offending part name") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())

                val ex = shouldThrow<PlanConversionException> {
                    ctx.converter.convertAndAppend(currentState)
                }
                ex.message shouldContain "planning"
            }
        }
    }

    // ── Validation: malformed JSON ──

    describe("GIVEN malformed plan_flow.json") {

        val malformedJson = """{ "parts": [ { invalid json } ] }"""

        describe("WHEN convertAndAppend is called") {

            it("THEN throws PlanConversionException") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(malformedJson)
                val currentState = CurrentState(parts = mutableListOf())

                val ex = shouldThrow<PlanConversionException> {
                    ctx.converter.convertAndAppend(currentState)
                }
                ex.message shouldContain "Failed to parse plan_flow.json"
            }
        }
    }

    // ── Validation: mixed phases ──

    describe("GIVEN a plan_flow.json with mixed execution and planning phases") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design the UI",
              "subParts": [
                { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" }
              ]
            },
            {
              "name": "planning_leftover",
              "phase": "planning",
              "description": "This should not be here",
              "subParts": [
                { "name": "plan", "role": "PLANNER", "agentType": "ClaudeCode", "model": "opus" }
              ]
            }
          ]
        }
        """.trimIndent()

        describe("WHEN convertAndAppend is called") {

            it("THEN throws PlanConversionException") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())

                shouldThrow<PlanConversionException> {
                    ctx.converter.convertAndAppend(currentState)
                }
            }
        }
    }

    // ── Directory structure created for new parts ──

    describe("GIVEN a valid plan_flow.json with execution part") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design the UI",
              "subParts": [
                { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" }
              ]
            }
          ]
        }
        """.trimIndent()

        describe("WHEN convertAndAppend is called") {

            it("THEN execution sub-part private directory is created") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                ctx.converter.convertAndAppend(currentState)
                ctx.aiOutputStructure.executionSubPartPrivateDir("ui_design", "impl").exists() shouldBe true
            }

            it("THEN feedback pending directory is created for the part") {
                val ctx = createTestContext()
                ctx.aiOutputStructure.planFlowJson().writeText(planFlowJson)
                val currentState = CurrentState(parts = mutableListOf())
                ctx.converter.convertAndAppend(currentState)
                ctx.aiOutputStructure.feedbackPendingDir("ui_design").exists() shouldBe true
            }
        }
    }
})
