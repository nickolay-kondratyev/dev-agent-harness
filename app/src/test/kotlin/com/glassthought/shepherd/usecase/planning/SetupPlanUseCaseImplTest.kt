package com.glassthought.shepherd.usecase.planning

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class SetupPlanUseCaseImplTest : AsgardDescribeSpec(body = {

    // ── Test data ────────────────────────────────────────────────────────────

    val straightforwardParts = listOf(
        Part(
            name = "build",
            phase = Phase.EXECUTION,
            description = "Build the project",
            subParts = listOf(
                SubPart(name = "impl", role = "CODER", agentType = "ClaudeCode", model = "sonnet"),
            ),
        ),
    )

    val detailedPlanningParts = listOf(
        Part(
            name = "design",
            phase = Phase.EXECUTION,
            description = "Design the architecture",
            subParts = emptyList(),
        ),
        Part(
            name = "implement",
            phase = Phase.EXECUTION,
            description = "Implement the design",
            subParts = emptyList(),
        ),
    )

    // ── Fakes ────────────────────────────────────────────────────────────────

    class FakeStraightforwardPlanUseCase(
        private val result: List<Part>,
    ) : StraightforwardPlanUseCase {
        var executeCalled = false

        override suspend fun execute(): List<Part> {
            executeCalled = true
            return result
        }
    }

    class FakeDetailedPlanningUseCase(
        private val result: List<Part>,
    ) : DetailedPlanningUseCase {
        var executeCalled = false

        override suspend fun execute(): List<Part> {
            executeCalled = true
            return result
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun buildStraightforwardWorkflow(): WorkflowDefinition =
        WorkflowDefinition(name = "straightforward-wf", parts = straightforwardParts)

    fun buildWithPlanningWorkflow(): WorkflowDefinition =
        WorkflowDefinition(
            name = "planning-wf",
            planningParts = listOf(
                Part(
                    name = "planner",
                    phase = Phase.PLANNING,
                    description = "Create plan",
                    subParts = emptyList(),
                ),
            ),
            executionPhasesFrom = "plan_flow.json",
        )

    // ── Straightforward routing ──────────────────────────────────────────────

    describe("GIVEN a straightforward workflow") {
        describe("WHEN setup is called") {
            it("THEN routes to StraightforwardPlanUseCase") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildStraightforwardWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                useCase.setup()
                straightforward.executeCalled shouldBe true
            }

            it("THEN does NOT route to DetailedPlanningUseCase") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildStraightforwardWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                useCase.setup()
                detailed.executeCalled shouldBe false
            }

            it("THEN returns the parts from StraightforwardPlanUseCase") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildStraightforwardWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                val result = useCase.setup()
                result shouldBe straightforwardParts
            }

            it("THEN returns exactly one part") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildStraightforwardWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                val result = useCase.setup()
                result shouldHaveSize 1
            }
        }
    }

    // ── With-planning routing ────────────────────────────────────────────────

    describe("GIVEN a with-planning workflow") {
        describe("WHEN setup is called") {
            it("THEN routes to DetailedPlanningUseCase") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildWithPlanningWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                useCase.setup()
                detailed.executeCalled shouldBe true
            }

            it("THEN does NOT route to StraightforwardPlanUseCase") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildWithPlanningWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                useCase.setup()
                straightforward.executeCalled shouldBe false
            }

            it("THEN returns the parts from DetailedPlanningUseCase") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildWithPlanningWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                val result = useCase.setup()
                result shouldBe detailedPlanningParts
            }

            it("THEN returns two parts") {
                val straightforward = FakeStraightforwardPlanUseCase(straightforwardParts)
                val detailed = FakeDetailedPlanningUseCase(detailedPlanningParts)
                val useCase = SetupPlanUseCaseImpl(
                    workflowDefinition = buildWithPlanningWorkflow(),
                    straightforwardPlanUseCase = straightforward,
                    detailedPlanningUseCase = detailed,
                    outFactory = outFactory,
                )

                val result = useCase.setup()
                result shouldHaveSize 2
            }
        }
    }
})
