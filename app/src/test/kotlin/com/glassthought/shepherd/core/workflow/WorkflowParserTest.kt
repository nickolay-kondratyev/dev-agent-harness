package com.glassthought.shepherd.core.workflow

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.state.Phase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class WorkflowParserTest : AsgardDescribeSpec({

    val parser = WorkflowParser.standard(outFactory)

    // ── Resolve project root (find dir containing config/workflows/) ──

    fun findProjectRoot(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        while (dir.parent != null) {
            if (dir.resolve("config/workflows/straightforward.json").toFile().exists()) {
                return dir
            }
            dir = dir.parent
        }
        error("Could not find project root containing config/workflows/")
    }

    // ── Parse actual straightforward.json ──

    describe("GIVEN the actual straightforward.json workflow file") {
        val projectRoot = findProjectRoot()

        describe("WHEN parsing 'straightforward'") {
            val definition = parser.parse("straightforward", projectRoot)

            it("THEN name is 'straightforward'") {
                definition.name shouldBe "straightforward"
            }

            it("THEN isStraightforward is true") {
                definition.isStraightforward shouldBe true
            }

            it("THEN isWithPlanning is false") {
                definition.isWithPlanning shouldBe false
            }

            it("THEN has one part") {
                definition.parts!! shouldHaveSize 1
            }

            it("THEN part name is 'main'") {
                definition.parts!![0].name shouldBe "main"
            }

            it("THEN part phase is EXECUTION") {
                definition.parts!![0].phase shouldBe Phase.EXECUTION
            }

            it("THEN part has two subParts") {
                definition.parts!![0].subParts shouldHaveSize 2
            }

            it("THEN first subPart role is IMPLEMENTATION_WITH_SELF_PLAN") {
                definition.parts!![0].subParts[0].role shouldBe "IMPLEMENTATION_WITH_SELF_PLAN"
            }

            it("THEN second subPart has iteration max of 4") {
                definition.parts!![0].subParts[1].iteration!!.max shouldBe 4
            }
        }
    }

    // ── Parse actual with-planning.json ──

    describe("GIVEN the actual with-planning.json workflow file") {
        val projectRoot = findProjectRoot()

        describe("WHEN parsing 'with-planning'") {
            val definition = parser.parse("with-planning", projectRoot)

            it("THEN name is 'with-planning'") {
                definition.name shouldBe "with-planning"
            }

            it("THEN isWithPlanning is true") {
                definition.isWithPlanning shouldBe true
            }

            it("THEN isStraightforward is false") {
                definition.isStraightforward shouldBe false
            }

            it("THEN has one planningPart") {
                definition.planningParts!! shouldHaveSize 1
            }

            it("THEN planningPart phase is PLANNING") {
                definition.planningParts!![0].phase shouldBe Phase.PLANNING
            }

            it("THEN planningPart has two subParts") {
                definition.planningParts!![0].subParts shouldHaveSize 2
            }

            it("THEN first subPart role is PLANNER") {
                definition.planningParts!![0].subParts[0].role shouldBe "PLANNER"
            }

            it("THEN second subPart role is PLAN_REVIEWER") {
                definition.planningParts!![0].subParts[1].role shouldBe "PLAN_REVIEWER"
            }

            it("THEN executionPhasesFrom is 'plan_flow.json'") {
                definition.executionPhasesFrom shouldBe "plan_flow.json"
            }

            it("THEN plan_review iteration max is 3") {
                definition.planningParts!![0].subParts[1].iteration!!.max shouldBe 3
            }
        }
    }

    // ── Fail-fast on missing file ──

    describe("GIVEN a non-existent workflow file") {
        val projectRoot = findProjectRoot()

        describe("WHEN parsing 'nonexistent-workflow'") {
            it("THEN throws IllegalArgumentException with 'not found'") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse("nonexistent-workflow", projectRoot)
                }
                exception.message shouldContain "not found"
            }
        }
    }

    // ── Fail-fast on malformed JSON ──

    describe("GIVEN a malformed JSON workflow file") {
        val tempDir = Files.createTempDirectory("workflow-parser-test")
        val workflowDir = tempDir.resolve("config/workflows")
        Files.createDirectories(workflowDir)
        Files.writeString(workflowDir.resolve("malformed.json"), "{ invalid json }")

        describe("WHEN parsing 'malformed'") {
            it("THEN throws IllegalArgumentException with 'Failed to parse'") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse("malformed", tempDir)
                }
                exception.message shouldContain "Failed to parse"
            }
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }

    // ── Phase validation: straightforward with wrong phase ──

    describe("GIVEN a straightforward workflow file with planning phase") {
        val tempDir = Files.createTempDirectory("workflow-parser-test")
        val workflowDir = tempDir.resolve("config/workflows")
        Files.createDirectories(workflowDir)

        val invalidJson = """
        {
          "name": "bad-phases",
          "parts": [
            {
              "name": "wrong",
              "phase": "planning",
              "description": "Should be execution",
              "subParts": [
                { "name": "impl", "role": "DOER", "agentType": "ClaudeCode", "model": "sonnet" }
              ]
            }
          ]
        }
        """.trimIndent()
        Files.writeString(workflowDir.resolve("bad-phases.json"), invalidJson)

        describe("WHEN parsing 'bad-phases'") {
            it("THEN throws IllegalArgumentException about phase='execution'") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse("bad-phases", tempDir)
                }
                exception.message shouldContain "phase='execution'"
            }
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }

    // ── Phase validation: with-planning with wrong phase ──

    describe("GIVEN a with-planning workflow file with execution phase in planningParts") {
        val tempDir = Files.createTempDirectory("workflow-parser-test")
        val workflowDir = tempDir.resolve("config/workflows")
        Files.createDirectories(workflowDir)

        val invalidJson = """
        {
          "name": "bad-planning-phases",
          "planningParts": [
            {
              "name": "wrong",
              "phase": "execution",
              "description": "Should be planning",
              "subParts": [
                { "name": "plan", "role": "PLANNER", "agentType": "ClaudeCode", "model": "opus" }
              ]
            }
          ],
          "executionPhasesFrom": "plan_flow.json"
        }
        """.trimIndent()
        Files.writeString(workflowDir.resolve("bad-planning-phases.json"), invalidJson)

        describe("WHEN parsing 'bad-planning-phases'") {
            it("THEN throws IllegalArgumentException about phase='planning'") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse("bad-planning-phases", tempDir)
                }
                exception.message shouldContain "phase='planning'"
            }
        }

        afterSpec {
            tempDir.toFile().deleteRecursively()
        }
    }
})
