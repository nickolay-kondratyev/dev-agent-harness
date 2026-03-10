package com.glassthought.chainsaw.core.workflow

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class WorkflowParserTest : AsgardDescribeSpec({

    fun resourcePath(name: String): Path =
        Path.of(
            WorkflowParserTest::class.java
                .getResource("/com/glassthought/chainsaw/core/workflow/$name")!!
                .toURI()
        )

    describe("GIVEN a straightforward workflow JSON") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN name is 'straightforward'") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.name shouldBe "straightforward"
            }

            it("THEN parts list has 1 entry") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!!.size shouldBe 1
            }

            it("THEN first part name is 'main'") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!![0].name shouldBe "main"
            }

            it("THEN first part description is 'Implement and review'") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!![0].description shouldBe "Implement and review"
            }

            it("THEN first part has 2 phases") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!![0].phases.size shouldBe 2
            }

            it("THEN first phase role is 'IMPLEMENTOR_WITH_SELF_PLAN'") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!![0].phases[0].role shouldBe "IMPLEMENTOR_WITH_SELF_PLAN"
            }

            it("THEN second phase role is 'IMPLEMENTATION_REVIEWER'") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!![0].phases[1].role shouldBe "IMPLEMENTATION_REVIEWER"
            }

            it("THEN first part iteration max is 4") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.parts!![0].iteration.max shouldBe 4
            }

            it("THEN planningPhases is null") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.planningPhases.shouldBeNull()
            }

            it("THEN planningIteration is null") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.planningIteration.shouldBeNull()
            }

            it("THEN executionPhasesFrom is null") {
                val definition = parser.parse(resourcePath("straightforward.json"))
                definition.executionPhasesFrom.shouldBeNull()
            }
        }
    }

    describe("GIVEN a with-planning workflow JSON") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN name is 'with-planning'") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.name shouldBe "with-planning"
            }

            it("THEN parts is null") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.parts.shouldBeNull()
            }

            it("THEN planningPhases has 2 entries") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.planningPhases!!.size shouldBe 2
            }

            it("THEN first planning phase role is 'PLANNER'") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.planningPhases!![0].role shouldBe "PLANNER"
            }

            it("THEN second planning phase role is 'PLAN_REVIEWER'") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.planningPhases!![1].role shouldBe "PLAN_REVIEWER"
            }

            it("THEN planningIteration max is 3") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.planningIteration!!.max shouldBe 3
            }

            it("THEN executionPhasesFrom is 'plan.json'") {
                val definition = parser.parse(resourcePath("with-planning.json"))
                definition.executionPhasesFrom shouldBe "plan.json"
            }
        }
    }

    describe("GIVEN a multi-part workflow JSON") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN parts list has 2 entries") {
                val definition = parser.parse(resourcePath("multi-part.json"))
                definition.parts!!.size shouldBe 2
            }

            it("THEN second part name is 'implementation'") {
                val definition = parser.parse(resourcePath("multi-part.json"))
                definition.parts!![1].name shouldBe "implementation"
            }

            it("THEN second part has 2 phases") {
                val definition = parser.parse(resourcePath("multi-part.json"))
                definition.parts!![1].phases.size shouldBe 2
            }

            it("THEN second part first phase role is 'IMPLEMENTOR'") {
                val definition = parser.parse(resourcePath("multi-part.json"))
                definition.parts!![1].phases[0].role shouldBe "IMPLEMENTOR"
            }

            it("THEN second part iteration max is 4") {
                val definition = parser.parse(resourcePath("multi-part.json"))
                definition.parts!![1].iteration.max shouldBe 4
            }
        }
    }

    describe("GIVEN a JSON file that does not exist") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws NoSuchFileException") {
                shouldThrow<NoSuchFileException> {
                    parser.parse(Path.of("/nonexistent/workflow.json"))
                }
            }
        }
    }

    describe("GIVEN a malformed JSON file") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws JsonProcessingException") {
                shouldThrow<JsonProcessingException> {
                    parser.parse(resourcePath("malformed.json"))
                }
            }
        }
    }

    describe("GIVEN a JSON file missing the name field") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws JsonProcessingException") {
                shouldThrow<JsonProcessingException> {
                    parser.parse(resourcePath("missing-name.json"))
                }
            }
        }
    }

    describe("GIVEN a JSON file with empty phases array in a part") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("empty-phases.json"))
                }
            }

            it("THEN exception message mentions the part name") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("empty-phases.json"))
                }
                exception.message shouldContain "broken-part"
            }
        }
    }

    describe("GIVEN a JSON file with neither parts nor planningPhases") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("neither-parts-nor-planning.json"))
                }
            }

            it("THEN exception message mentions the missing fields") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("neither-parts-nor-planning.json"))
                }
                exception.message shouldContain "neither"
            }
        }
    }

    describe("GIVEN a JSON file with blank name") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("blank-name.json"))
                }
            }

            it("THEN exception message mentions blank name") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("blank-name.json"))
                }
                exception.message shouldContain "blank"
            }
        }
    }

    describe("GIVEN a with-planning JSON missing planningIteration") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("planning-missing-iteration.json"))
                }
            }

            it("THEN exception message mentions planningIteration") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("planning-missing-iteration.json"))
                }
                exception.message shouldContain "planningIteration"
            }
        }
    }

    describe("GIVEN a with-planning JSON missing executionPhasesFrom") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("planning-missing-execution-from.json"))
                }
            }

            it("THEN exception message mentions executionPhasesFrom") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("planning-missing-execution-from.json"))
                }
                exception.message shouldContain "executionPhasesFrom"
            }
        }
    }

    describe("GIVEN a JSON file with both parts and planningPhases") {
        val parser = WorkflowParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("both-parts-and-planning.json"))
                }
            }

            it("THEN exception message mentions mutual exclusivity") {
                val exception = shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("both-parts-and-planning.json"))
                }
                exception.message shouldContain "not both"
            }
        }
    }
})
