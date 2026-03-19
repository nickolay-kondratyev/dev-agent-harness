package com.glassthought.shepherd.core.workflow

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class WorkflowDefinitionTest : AsgardDescribeSpec({

    // ── Helper factories ──

    fun executionPart(name: String = "main") = Part(
        name = name,
        phase = Phase.EXECUTION,
        description = "test part",
        subParts = listOf(
            SubPart(name = "impl", role = "IMPLEMENTER", agentType = "ClaudeCode", model = "sonnet"),
        ),
    )

    fun planningPart(name: String = "planning") = Part(
        name = name,
        phase = Phase.PLANNING,
        description = "plan part",
        subParts = listOf(
            SubPart(name = "plan", role = "PLANNER", agentType = "ClaudeCode", model = "opus"),
        ),
    )

    // ── Straightforward workflow ──

    describe("GIVEN a straightforward WorkflowDefinition") {
        val definition = WorkflowDefinition(
            name = "straightforward",
            parts = listOf(executionPart()),
        )

        it("THEN isStraightforward is true") {
            definition.isStraightforward shouldBe true
        }

        it("THEN isWithPlanning is false") {
            definition.isWithPlanning shouldBe false
        }

        it("THEN parts is not null") {
            definition.parts!!.size shouldBe 1
        }
    }

    // ── With-planning workflow ──

    describe("GIVEN a with-planning WorkflowDefinition") {
        val definition = WorkflowDefinition(
            name = "with-planning",
            planningParts = listOf(planningPart()),
            executionPhasesFrom = "plan_flow.json",
        )

        it("THEN isWithPlanning is true") {
            definition.isWithPlanning shouldBe true
        }

        it("THEN isStraightforward is false") {
            definition.isStraightforward shouldBe false
        }

        it("THEN executionPhasesFrom is set") {
            definition.executionPhasesFrom shouldBe "plan_flow.json"
        }
    }

    // ── Mutual exclusivity validation ──

    describe("GIVEN both parts and planningParts are provided") {
        it("THEN construction fails with IllegalArgumentException") {
            val exception = shouldThrow<IllegalArgumentException> {
                WorkflowDefinition(
                    name = "invalid",
                    parts = listOf(executionPart()),
                    planningParts = listOf(planningPart()),
                    executionPhasesFrom = "plan_flow.json",
                )
            }
            exception.message shouldContain "exactly one of"
        }
    }

    describe("GIVEN neither parts nor planningParts are provided") {
        it("THEN construction fails with IllegalArgumentException") {
            val exception = shouldThrow<IllegalArgumentException> {
                WorkflowDefinition(name = "empty")
            }
            exception.message shouldContain "exactly one of"
        }
    }

    // ── executionPhasesFrom rejected for straightforward ──

    describe("GIVEN a straightforward workflow with executionPhasesFrom specified") {
        it("THEN construction fails with IllegalArgumentException") {
            val exception = shouldThrow<IllegalArgumentException> {
                WorkflowDefinition(
                    name = "bad-straightforward",
                    parts = listOf(executionPart()),
                    executionPhasesFrom = "plan_flow.json",
                )
            }
            exception.message shouldContain "Straightforward workflow must not specify 'executionPhasesFrom'"
        }
    }

    // ── allPartsForStructure() ──

    describe("GIVEN a straightforward WorkflowDefinition with allPartsForStructure()") {
        val definition = WorkflowDefinition(
            name = "straightforward",
            parts = listOf(executionPart("main"), executionPart("secondary")),
        )

        it("THEN allPartsForStructure() returns execution parts") {
            definition.allPartsForStructure() shouldBe definition.parts
        }

        it("THEN allPartsForStructure() returns the expected number of parts") {
            definition.allPartsForStructure().size shouldBe 2
        }
    }

    describe("GIVEN a with-planning WorkflowDefinition with allPartsForStructure()") {
        val definition = WorkflowDefinition(
            name = "with-planning",
            planningParts = listOf(planningPart("plan"), planningPart("review")),
            executionPhasesFrom = "plan_flow.json",
        )

        it("THEN allPartsForStructure() returns planning parts") {
            definition.allPartsForStructure() shouldBe definition.planningParts
        }

        it("THEN allPartsForStructure() returns the expected number of parts") {
            definition.allPartsForStructure().size shouldBe 2
        }
    }

    // ── executionPhasesFrom required for with-planning ──

    describe("GIVEN planningParts without executionPhasesFrom") {
        it("THEN construction fails with IllegalArgumentException") {
            val exception = shouldThrow<IllegalArgumentException> {
                WorkflowDefinition(
                    name = "missing-execution-phases-from",
                    planningParts = listOf(planningPart()),
                )
            }
            exception.message shouldContain "executionPhasesFrom"
        }
    }
})
