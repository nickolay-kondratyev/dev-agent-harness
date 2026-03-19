package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CurrentStateInitializerTest : AsgardDescribeSpec({

    val initializer = CurrentStateInitializerImpl()

    // ── Straightforward workflow ──

    describe("GIVEN a straightforward workflow definition") {

        val workflowDef = WorkflowDefinition(
            name = "test-straightforward",
            parts = listOf(
                Part(
                    name = "ui_design",
                    phase = Phase.EXECUTION,
                    description = "Design the UI",
                    subParts = listOf(
                        SubPart(name = "impl", role = "UI_DESIGNER", agentType = "ClaudeCode", model = "sonnet"),
                        SubPart(
                            name = "review",
                            role = "UI_REVIEWER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            iteration = IterationConfig(max = 3),
                        ),
                    ),
                ),
                Part(
                    name = "backend",
                    phase = Phase.EXECUTION,
                    description = "Implement backend",
                    subParts = listOf(
                        SubPart(name = "impl", role = "BACKEND_DEV", agentType = "ClaudeCode", model = "opus"),
                    ),
                ),
            ),
        )

        describe("WHEN creating initial state") {
            val state = initializer.createInitialState(workflowDef)

            it("THEN has two parts") {
                state.parts shouldHaveSize 2
            }

            it("THEN first part name is ui_design") {
                state.parts[0].name shouldBe "ui_design"
            }

            it("THEN second part name is backend") {
                state.parts[1].name shouldBe "backend"
            }

            it("THEN all parts have EXECUTION phase") {
                state.parts[0].phase shouldBe Phase.EXECUTION
                state.parts[1].phase shouldBe Phase.EXECUTION
            }
        }

        describe("WHEN inspecting doer sub-part status") {
            val state = initializer.createInitialState(workflowDef)
            val implSubPart = state.parts[0].subParts[0]

            it("THEN status is NOT_STARTED") {
                implSubPart.status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN iteration is null (doer has no iteration config)") {
                implSubPart.iteration.shouldBeNull()
            }

            it("THEN sessionIds is null") {
                implSubPart.sessionIds.shouldBeNull()
            }
        }

        describe("WHEN inspecting reviewer sub-part status") {
            val state = initializer.createInitialState(workflowDef)
            val reviewSubPart = state.parts[0].subParts[1]

            it("THEN status is NOT_STARTED") {
                reviewSubPart.status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN iteration.current is 0") {
                reviewSubPart.iteration.shouldNotBeNull()
                reviewSubPart.iteration!!.current shouldBe 0
            }

            it("THEN iteration.max is preserved from workflow definition") {
                reviewSubPart.iteration!!.max shouldBe 3
            }

            it("THEN sessionIds is null") {
                reviewSubPart.sessionIds.shouldBeNull()
            }
        }

        describe("WHEN inspecting doer-only part (backend)") {
            val state = initializer.createInitialState(workflowDef)
            val backendImpl = state.parts[1].subParts[0]

            it("THEN status is NOT_STARTED") {
                backendImpl.status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN iteration is null") {
                backendImpl.iteration.shouldBeNull()
            }
        }
    }

    // ── With-planning workflow ──

    describe("GIVEN a with-planning workflow definition") {

        val workflowDef = WorkflowDefinition(
            name = "test-with-planning",
            planningParts = listOf(
                Part(
                    name = "planning",
                    phase = Phase.PLANNING,
                    description = "Plan the workflow",
                    subParts = listOf(
                        SubPart(name = "plan", role = "PLANNER", agentType = "ClaudeCode", model = "opus"),
                        SubPart(
                            name = "plan_review",
                            role = "PLAN_REVIEWER",
                            agentType = "ClaudeCode",
                            model = "opus",
                            iteration = IterationConfig(max = 3),
                        ),
                    ),
                ),
            ),
            executionPhasesFrom = "plan_flow.json",
        )

        describe("WHEN creating initial state") {
            val state = initializer.createInitialState(workflowDef)

            it("THEN has exactly one part (planning only)") {
                state.parts shouldHaveSize 1
            }

            it("THEN part name is planning") {
                state.parts[0].name shouldBe "planning"
            }

            it("THEN part phase is PLANNING") {
                state.parts[0].phase shouldBe Phase.PLANNING
            }

            it("THEN has two sub-parts") {
                state.parts[0].subParts shouldHaveSize 2
            }
        }

        describe("WHEN inspecting planner sub-part") {
            val state = initializer.createInitialState(workflowDef)
            val planSubPart = state.parts[0].subParts[0]

            it("THEN status is NOT_STARTED") {
                planSubPart.status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN iteration is null (planner is a doer)") {
                planSubPart.iteration.shouldBeNull()
            }
        }

        describe("WHEN inspecting plan_reviewer sub-part") {
            val state = initializer.createInitialState(workflowDef)
            val reviewSubPart = state.parts[0].subParts[1]

            it("THEN status is NOT_STARTED") {
                reviewSubPart.status shouldBe SubPartStatus.NOT_STARTED
            }

            it("THEN iteration.current is 0") {
                reviewSubPart.iteration.shouldNotBeNull()
                reviewSubPart.iteration!!.current shouldBe 0
            }

            it("THEN iteration.max is 3") {
                reviewSubPart.iteration!!.max shouldBe 3
            }
        }
    }

    // ── Idempotency: workflow sub-parts that already have current set ──

    describe("GIVEN workflow definition with iteration.current already set (e.g. from JSON)") {
        val workflowDef = WorkflowDefinition(
            name = "test-reset",
            parts = listOf(
                Part(
                    name = "part1",
                    phase = Phase.EXECUTION,
                    description = "Part with pre-set current",
                    subParts = listOf(
                        SubPart(
                            name = "review",
                            role = "REVIEWER",
                            agentType = "ClaudeCode",
                            model = "sonnet",
                            iteration = IterationConfig(max = 5, current = 2),
                        ),
                    ),
                ),
            ),
        )

        describe("WHEN creating initial state") {
            val state = initializer.createInitialState(workflowDef)
            val reviewSubPart = state.parts[0].subParts[0]

            it("THEN iteration.current is reset to 0") {
                reviewSubPart.iteration!!.current shouldBe 0
            }

            it("THEN iteration.max is preserved") {
                reviewSubPart.iteration!!.max shouldBe 5
            }
        }
    }
})
