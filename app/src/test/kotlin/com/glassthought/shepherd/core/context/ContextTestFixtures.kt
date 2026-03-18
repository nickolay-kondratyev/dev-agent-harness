package com.glassthought.shepherd.core.context

import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test fixtures for [ContextForAgentProvider] tests.
 *
 * Creates temporary directories and files needed by the provider. Each call to a factory
 * method sets up a self-contained filesystem structure under the provided [tempDir].
 */
object ContextTestFixtures {

    fun resourceDir(name: String): Path =
        Path.of(
            ContextTestFixtures::class.java
                .getResource("/com/glassthought/shepherd/core/context/fixtures/$name")!!
                .toURI()
        )

    fun roleDefinition(roleName: String): RoleDefinition {
        val rolePath = resourceDir("roles").resolve("$roleName.md")
        return RoleDefinition(
            name = roleName,
            description = "Test role: $roleName",
            descriptionLong = null,
            filePath = rolePath,
        )
    }

    /**
     * Creates a minimal doer instruction request with all required filesystem structures.
     */
    fun doerInstructionRequest(tempDir: Path): AgentInstructionRequest.DoerRequest {
        val outputDir = tempDir.resolve("comm/in")
        Files.createDirectories(outputDir)

        val publicMdOutputPath = tempDir.resolve("comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return AgentInstructionRequest.DoerRequest(
            roleDefinition = roleDefinition("IMPLEMENTOR"),
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            iterationNumber = 1,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
            executionContext = ExecutionContext(
                partName = "part_1_implementation",
                partDescription = "Implement the main feature",
                planMdPath = null,
                priorPublicMdPaths = emptyList(),
            ),
            reviewerPublicMdPath = null,
        )
    }

    /**
     * Creates a reviewer instruction request on iteration 1 with doer's PUBLIC.md.
     */
    fun reviewerInstructionRequest(tempDir: Path): AgentInstructionRequest.ReviewerRequest {
        val outputDir = tempDir.resolve("reviewer/comm/in")
        Files.createDirectories(outputDir)

        val doerPublicMd = tempDir.resolve("doer/comm/out/PUBLIC.md")
        Files.createDirectories(doerPublicMd.parent)
        Files.writeString(doerPublicMd, "# Doer Output\n\nImplemented feature X.")

        val publicMdOutputPath = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        val feedbackDir = tempDir.resolve("doer/__feedback")
        Files.createDirectories(feedbackDir)

        return AgentInstructionRequest.ReviewerRequest(
            roleDefinition = roleDefinition("REVIEWER"),
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            iterationNumber = 1,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
            executionContext = ExecutionContext(
                partName = "part_1_implementation",
                partDescription = "Review the implementation",
                planMdPath = null,
                priorPublicMdPaths = emptyList(),
            ),
            doerPublicMdPath = doerPublicMd,
            feedbackDir = feedbackDir,
        )
    }

    /**
     * Creates a reviewer instruction request on iteration > 1 with feedback directory.
     */
    fun reviewerInstructionRequestWithFeedback(tempDir: Path): AgentInstructionRequest.ReviewerRequest {
        val outputDir = tempDir.resolve("reviewer/comm/in")
        Files.createDirectories(outputDir)

        val doerPublicMd = tempDir.resolve("doer/comm/out/PUBLIC.md")
        Files.createDirectories(doerPublicMd.parent)
        Files.writeString(doerPublicMd, "# Doer Output\n\nImplemented feature X.")

        val publicMdOutputPath = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        // Use test resource feedback directory
        val feedbackDir = resourceDir("feedback")

        return AgentInstructionRequest.ReviewerRequest(
            roleDefinition = roleDefinition("REVIEWER"),
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            iterationNumber = 2,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
            executionContext = ExecutionContext(
                partName = "part_1_implementation",
                partDescription = "Review the implementation",
                planMdPath = null,
                priorPublicMdPaths = emptyList(),
            ),
            doerPublicMdPath = doerPublicMd,
            feedbackDir = feedbackDir,
        )
    }

    /**
     * Creates a planner instruction request.
     */
    fun plannerRequest(tempDir: Path): AgentInstructionRequest.PlannerRequest {
        val outputDir = tempDir.resolve("planner/comm/in")
        Files.createDirectories(outputDir)

        val planJsonOutputPath = tempDir.resolve("harness_private/plan_flow.json")
        Files.createDirectories(planJsonOutputPath.parent)

        val planMdOutputPath = tempDir.resolve("shared/plan/PLAN.md")
        Files.createDirectories(planMdOutputPath.parent)

        val publicMdOutputPath = tempDir.resolve("planner/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return AgentInstructionRequest.PlannerRequest(
            roleDefinition = roleDefinition("PLANNER"),
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            roleCatalogEntries = listOf(
                RoleCatalogEntry("IMPLEMENTOR", "Implements features", "Full-stack agent"),
                RoleCatalogEntry("REVIEWER", "Reviews code", null),
            ),
            iterationNumber = 1,
            planReviewerPublicMdPath = null,
            planJsonOutputPath = planJsonOutputPath,
            planMdOutputPath = planMdOutputPath,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
        )
    }

    /**
     * Creates a plan reviewer instruction request.
     */
    fun planReviewerRequest(tempDir: Path): AgentInstructionRequest.PlanReviewerRequest {
        val outputDir = tempDir.resolve("plan_reviewer/comm/in")
        Files.createDirectories(outputDir)

        val plannerPublicMd = tempDir.resolve("planner/comm/out/PUBLIC.md")
        Files.createDirectories(plannerPublicMd.parent)
        Files.writeString(plannerPublicMd, "# Planner Rationale\n\nChose 3-part approach.")

        val publicMdOutputPath = tempDir.resolve("plan_reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return AgentInstructionRequest.PlanReviewerRequest(
            roleDefinition = roleDefinition("PLAN_REVIEWER"),
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            planJsonContent = """{"parts": []}""",
            planMdContent = "# Plan\n\nThree-part implementation.",
            plannerPublicMdPath = plannerPublicMd,
            iterationNumber = 1,
            priorPlanReviewerPublicMdPath = null,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
        )
    }
}
