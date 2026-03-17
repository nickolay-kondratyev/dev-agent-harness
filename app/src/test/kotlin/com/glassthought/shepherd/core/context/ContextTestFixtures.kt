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
    fun doerInstructionRequest(tempDir: Path): DoerInstructionRequest {
        val outputDir = tempDir.resolve("comm/in")
        Files.createDirectories(outputDir)

        val publicMdOutputPath = tempDir.resolve("comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return DoerInstructionRequest(
            roleDefinition = roleDefinition("IMPLEMENTOR"),
            partName = "part_1_implementation",
            partDescription = "Implement the main feature",
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            planMdPath = null,
            priorPublicMdPaths = emptyList(),
            iterationNumber = 1,
            reviewerPublicMdPath = null,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
        )
    }

    /**
     * Creates a reviewer instruction request on iteration 1 with doer's PUBLIC.md.
     */
    fun reviewerInstructionRequest(tempDir: Path): ReviewerInstructionRequest {
        val outputDir = tempDir.resolve("reviewer/comm/in")
        Files.createDirectories(outputDir)

        val doerPublicMd = tempDir.resolve("doer/comm/out/PUBLIC.md")
        Files.createDirectories(doerPublicMd.parent)
        Files.writeString(doerPublicMd, "# Doer Output\n\nImplemented feature X.")

        val publicMdOutputPath = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return ReviewerInstructionRequest(
            roleDefinition = roleDefinition("REVIEWER"),
            partName = "part_1_implementation",
            partDescription = "Review the implementation",
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            planMdPath = null,
            priorPublicMdPaths = emptyList(),
            iterationNumber = 1,
            doerPublicMdPath = doerPublicMd,
            feedbackDir = null,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
        )
    }

    /**
     * Creates a reviewer instruction request on iteration > 1 with feedback directory.
     */
    fun reviewerInstructionRequestWithFeedback(tempDir: Path): ReviewerInstructionRequest {
        val outputDir = tempDir.resolve("reviewer/comm/in")
        Files.createDirectories(outputDir)

        val doerPublicMd = tempDir.resolve("doer/comm/out/PUBLIC.md")
        Files.createDirectories(doerPublicMd.parent)
        Files.writeString(doerPublicMd, "# Doer Output\n\nImplemented feature X.")

        val publicMdOutputPath = tempDir.resolve("reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        // Use test resource feedback directory
        val feedbackDir = resourceDir("feedback")

        return ReviewerInstructionRequest(
            roleDefinition = roleDefinition("REVIEWER"),
            partName = "part_1_implementation",
            partDescription = "Review the implementation",
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            planMdPath = null,
            priorPublicMdPaths = emptyList(),
            iterationNumber = 2,
            doerPublicMdPath = doerPublicMd,
            feedbackDir = feedbackDir,
            outputDir = outputDir,
            publicMdOutputPath = publicMdOutputPath,
        )
    }

    /**
     * Creates a planner instruction request.
     */
    fun plannerRequest(tempDir: Path): PlannerInstructionRequest {
        val outputDir = tempDir.resolve("planner/comm/in")
        Files.createDirectories(outputDir)

        val planJsonOutputPath = tempDir.resolve("harness_private/plan.json")
        Files.createDirectories(planJsonOutputPath.parent)

        val planMdOutputPath = tempDir.resolve("shared/plan/PLAN.md")
        Files.createDirectories(planMdOutputPath.parent)

        val publicMdOutputPath = tempDir.resolve("planner/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return PlannerInstructionRequest(
            roleDefinition = roleDefinition("PLANNER"),
            ticketContent = "---\nid: test-001\ntitle: Test Ticket\n---\n\nImplement feature X.",
            roleCatalogEntries = listOf(
                InstructionRenderers.RoleCatalogEntry("IMPLEMENTOR", "Implements features", "Full-stack agent"),
                InstructionRenderers.RoleCatalogEntry("REVIEWER", "Reviews code", null),
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
    fun planReviewerRequest(tempDir: Path): PlanReviewerInstructionRequest {
        val outputDir = tempDir.resolve("plan_reviewer/comm/in")
        Files.createDirectories(outputDir)

        val plannerPublicMd = tempDir.resolve("planner/comm/out/PUBLIC.md")
        Files.createDirectories(plannerPublicMd.parent)
        Files.writeString(plannerPublicMd, "# Planner Rationale\n\nChose 3-part approach.")

        val publicMdOutputPath = tempDir.resolve("plan_reviewer/comm/out/PUBLIC.md")
        Files.createDirectories(publicMdOutputPath.parent)

        return PlanReviewerInstructionRequest(
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
