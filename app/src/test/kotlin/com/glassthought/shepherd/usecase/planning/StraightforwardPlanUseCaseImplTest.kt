package com.glassthought.shepherd.usecase.planning

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class StraightforwardPlanUseCaseImplTest : AsgardDescribeSpec(body = {

    val sampleParts = listOf(
        Part(
            name = "implementation",
            phase = Phase.EXECUTION,
            description = "Implement the feature",
            subParts = listOf(
                SubPart(name = "impl", role = "CODER", agentType = "ClaudeCode", model = "sonnet"),
            ),
        ),
        Part(
            name = "testing",
            phase = Phase.EXECUTION,
            description = "Write tests",
            subParts = emptyList(),
        ),
    )

    fun buildWorkflowDefinition(parts: List<Part>): WorkflowDefinition =
        WorkflowDefinition(name = "test-workflow", parts = parts)

    fun buildUseCase(parts: List<Part>): StraightforwardPlanUseCaseImpl =
        StraightforwardPlanUseCaseImpl(
            workflowDefinition = buildWorkflowDefinition(parts),
            outFactory = outFactory,
        )

    describe("GIVEN a straightforward workflow with predefined parts") {
        describe("WHEN execute is called") {
            it("THEN returns the parts from the workflow definition") {
                val useCase = buildUseCase(sampleParts)
                val result = useCase.execute()
                result shouldBe sampleParts
            }

            it("THEN returns the correct number of parts") {
                val useCase = buildUseCase(sampleParts)
                val result = useCase.execute()
                result shouldHaveSize 2
            }
        }
    }

    describe("GIVEN a straightforward workflow with a single part") {
        describe("WHEN execute is called") {
            it("THEN returns a list with one part") {
                val singlePart = listOf(sampleParts.first())
                val useCase = buildUseCase(singlePart)
                val result = useCase.execute()
                result shouldHaveSize 1
            }

            it("THEN the returned part matches the workflow definition") {
                val singlePart = listOf(sampleParts.first())
                val useCase = buildUseCase(singlePart)
                val result = useCase.execute()
                result.first().name shouldBe "implementation"
            }
        }
    }
})
