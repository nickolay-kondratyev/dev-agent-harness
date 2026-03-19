package com.glassthought.shepherd.integtest.feedback

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.ContextWindowState
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.FakeAgentFacade
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.context.ExecutionContext
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.executor.InnerFeedbackLoop
import com.glassthought.shepherd.core.executor.InnerFeedbackLoopDeps
import com.glassthought.shepherd.core.executor.InnerLoopContext
import com.glassthought.shepherd.core.executor.InnerLoopOutcome
import com.glassthought.shepherd.core.executor.PartCompletionGuard
import com.glassthought.shepherd.core.executor.PublicMdValidator
import com.glassthought.shepherd.core.executor.SubPartConfig
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.usecase.rejectionnegotiation.FeedbackFileReader
import com.glassthought.shepherd.usecase.rejectionnegotiation.InstructionFileWriter
import com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionNegotiationUseCaseImpl
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwait
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructOutcome
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Gate 6: Integration validation for the granular feedback loop.
 *
 * Wires REAL instances of [InnerFeedbackLoop], [PartCompletionGuard],
 * [RejectionNegotiationUseCaseImpl], and [FeedbackResolutionParser] together,
 * using [FakeAgentFacade] and fake [ReInstructAndAwait] to simulate agent behavior.
 *
 * This validates that all feedback loop components integrate correctly:
 * - Severity-ordered processing (critical -> important -> optional)
 * - Harness-owned file movement (pending -> addressed/rejected)
 * - Rejection negotiation (accept, insist -> comply, insist -> defy)
 * - Part completion guard enforcement
 * - Iteration counter semantics
 * - Feedback files presence guard
 * - Optional item SKIPPED handling
 *
 * WHY-NOT self-compaction scenario: [InnerFeedbackLoop] reads context window state per item
 * but does NOT own the compaction decision — that responsibility belongs to [PartExecutorImpl.afterDone()].
 * The inner loop only checks if context is low and logs. There is no integration surface to test
 * self-compaction at this layer. The unit test [InnerFeedbackLoopTest] verifies that
 * `readContextWindowState` is called per item, which is the extent of this component's responsibility.
 *
 * See spec: ref.ap.5Y5s8gqykzGN1TVK5MZdS.E (granular-feedback-loop.md, Gate 6)
 */
class GranularFeedbackLoopIntegTest : AsgardDescribeSpec({

    // -- Shared helpers ------------------------------------------

    fun buildHandle(
        guidSuffix: String,
        sessionId: String,
    ): SpawnedAgentHandle {
        val guid = HandshakeGuid("handshake.$guidSuffix")
        return SpawnedAgentHandle(
            guid = guid,
            sessionId = ResumableAgentSessionId(
                handshakeGuid = guid,
                agentType = AgentType.CLAUDE_CODE,
                sessionId = sessionId,
                model = "test-model",
            ),
            lastActivityTimestamp = Instant.EPOCH,
        )
    }

    val doerHandle = buildHandle("doer", "session-doer")
    val reviewerHandle = buildHandle("reviewer", "session-reviewer")

    fun createFeedbackDir(): Path {
        val dir = Files.createTempDirectory("feedback-integ")
        Files.createDirectories(dir.resolve("pending"))
        Files.createDirectories(dir.resolve("addressed"))
        Files.createDirectories(dir.resolve("rejected"))
        return dir
    }

    fun writePendingFile(
        feedbackDir: Path,
        filename: String,
        content: String = "# Feedback\nSome issue.",
    ): Path {
        val file = feedbackDir.resolve("pending").resolve(filename)
        Files.writeString(file, content)
        return file
    }

    fun createDoerConfigWithPublicMd(): SubPartConfig {
        val dir = Files.createTempDirectory("doer-output")
        val publicMd = dir.resolve("PUBLIC.md")
        Files.writeString(publicMd, "# Output\nDone.")
        return SubPartConfig(
            partName = "part_1",
            subPartName = "doer",
            subPartIndex = 0,
            subPartRole = SubPartRole.DOER,
            agentType = AgentType.CLAUDE_CODE,
            model = "test-model",
            systemPromptPath = Path.of("/tmp/prompt.md"),
            bootstrapMessage = "bootstrap",
            roleDefinition = RoleDefinition(
                name = "DOER",
                description = "test doer role",
                descriptionLong = null,
                filePath = Path.of("/tmp/roles/doer.md"),
            ),
            ticketContent = "Test ticket",
            outputDir = dir,
            publicMdOutputPath = publicMd,
            privateMdPath = null,
            executionContext = ExecutionContext(
                partName = "part_1",
                partDescription = "Test part",
                planMdPath = null,
                priorPublicMdPaths = emptyList(),
            ),
        )
    }

    class RecordingGitStrategy : GitCommitStrategy {
        val calls = mutableListOf<SubPartDoneContext>()
        override suspend fun onSubPartDone(context: SubPartDoneContext) {
            calls.add(context)
        }
    }

    /**
     * FeedbackFileReader backed by a mutable map of file path -> content.
     * Supports queue-based reading: each path can have a list of responses
     * (first read returns index 0, second returns index 1, etc.).
     */
    class QueueBasedFeedbackFileReader : FeedbackFileReader {
        private val contentQueues = mutableMapOf<String, MutableList<String>>()

        fun enqueue(path: Path, vararg contents: String) {
            contentQueues.getOrPut(path.toString()) { mutableListOf() }
                .addAll(contents)
        }

        override suspend fun readContent(path: Path): String {
            val key = path.toString()
            val queue = contentQueues[key]
                ?: error("No content queued for path=[$key]")
            check(queue.isNotEmpty()) {
                "Content queue exhausted for path=[$key]"
            }
            return queue.removeFirst()
        }
    }

    /** Fake ContextForAgentProvider that returns a dummy instruction file. */
    fun fakeContextProvider(): ContextForAgentProvider {
        val instructionFile = Files.createTempFile("instructions", ".md")
        Files.writeString(instructionFile, "# Instructions")
        return ContextForAgentProvider { _ -> instructionFile }
    }

    /** Fake InstructionFileWriter that writes to temp files. */
    fun fakeInstructionFileWriter() = InstructionFileWriter { content, label ->
        val file = Files.createTempFile("integ-$label-", ".md")
        Files.writeString(file, content)
        file
    }

    /**
     * Builds a fully wired [InnerFeedbackLoop] with REAL [RejectionNegotiationUseCaseImpl],
     * REAL [PartCompletionGuard], REAL [PublicMdValidator], and REAL [FeedbackResolutionParser]
     * (used internally by InnerFeedbackLoop and RejectionNegotiationUseCaseImpl).
     *
     * Only [ReInstructAndAwait], [FeedbackFileReader], [AgentFacade], and [GitCommitStrategy]
     * are faked — those are the boundaries to agent communication and external systems.
     */
    data class WiredLoopSetup(
        val loop: InnerFeedbackLoop,
        val gitStrategy: RecordingGitStrategy,
    )

    fun buildWiredLoop(
        reInstructAndAwait: ReInstructAndAwait,
        feedbackFileReader: FeedbackFileReader,
        gitStrategy: RecordingGitStrategy = RecordingGitStrategy(),
        facade: FakeAgentFacade = FakeAgentFacade(),
    ): WiredLoopSetup {
        facade.onReadContextWindowState {
            ContextWindowState(remainingPercentage = 80)
        }

        val rejectionUseCase = RejectionNegotiationUseCaseImpl(
            reInstructAndAwait = reInstructAndAwait,
            feedbackFileReader = feedbackFileReader,
            instructionFileWriter = fakeInstructionFileWriter(),
            outFactory = outFactory,
        )

        val loop = InnerFeedbackLoop(
            InnerFeedbackLoopDeps(
                reInstructAndAwait = reInstructAndAwait,
                rejectionNegotiationUseCase = rejectionUseCase,
                contextForAgentProvider = fakeContextProvider(),
                agentFacade = facade,
                gitCommitStrategy = gitStrategy,
                publicMdValidator = PublicMdValidator(),
                feedbackFileReader = feedbackFileReader,
                outFactory = outFactory,
            )
        )
        return WiredLoopSetup(loop, gitStrategy)
    }

    fun buildCtx(
        feedbackDir: Path,
        doerConfig: SubPartConfig,
        currentIteration: Int = 1,
        maxIterations: Int = 5,
    ) = InnerLoopContext(
        doerHandle = doerHandle,
        reviewerHandle = reviewerHandle,
        feedbackDir = feedbackDir,
        doerConfig = doerConfig,
        currentIteration = currentIteration,
        maxIterations = maxIterations,
    )

    fun listFiles(dir: Path): List<String> =
        if (Files.exists(dir)) {
            Files.list(dir).use { s ->
                s.filter { Files.isRegularFile(it) }
                    .map { it.fileName.toString() }
                    .sorted()
                    .toList()
            }
        } else {
            emptyList()
        }

    // ----------------------------------------------------------
    // Scenario 1: Happy path — ADDRESSED feedback
    // ----------------------------------------------------------

    describe("GIVEN reviewer writes critical, important, and optional feedback") {
        describe("WHEN doer addresses every item with ADDRESSED resolution") {

            // Shared state for this describe block's `it` assertions
            lateinit var feedbackDir: Path
            lateinit var gitStrategy: RecordingGitStrategy
            lateinit var result: InnerLoopOutcome

            beforeEach {
                feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()

                writePendingFile(feedbackDir, "optional__style-nit.md", "Fix style.")
                writePendingFile(feedbackDir, "critical__null-check.md", "Add null check.")
                writePendingFile(feedbackDir, "important__error-handling.md", "Handle error.")

                val reader = QueueBasedFeedbackFileReader()
                // Each file is read TWICE: once for instruction assembly, once for resolution parsing
                val pending = feedbackDir.resolve("pending")
                for (file in Files.list(pending).use { it.toList() }) {
                    reader.enqueue(
                        file,
                        file.fileName.toString(), // first read: original content for instructions
                        "## Resolution: ADDRESSED\nFixed.", // second read: resolution marker
                    )
                }

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                }

                val setup = buildWiredLoop(reInstruct, reader)
                gitStrategy = setup.gitStrategy
                result = setup.loop.execute(buildCtx(feedbackDir, doerCfg))
            }

            it("THEN result is Continue") {
                result shouldBe InnerLoopOutcome.Continue
            }

            it("THEN all files move to addressed/ in severity order") {
                val addressedFiles = listFiles(feedbackDir.resolve("addressed"))
                addressedFiles shouldContainExactly listOf(
                    "critical__null-check.md",
                    "important__error-handling.md",
                    "optional__style-nit.md",
                )
            }

            it("THEN pending/ is empty") {
                listFiles(feedbackDir.resolve("pending")) shouldHaveSize 0
            }

            it("THEN git commit is called once per feedback item") {
                gitStrategy.calls shouldHaveSize 3
            }

            it("THEN iteration counter in git commits remains constant") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()

                writePendingFile(feedbackDir, "critical__a.md", "Issue A.")
                writePendingFile(feedbackDir, "critical__b.md", "Issue B.")

                val reader = QueueBasedFeedbackFileReader()
                for (file in Files.list(feedbackDir.resolve("pending")).use { it.toList() }) {
                    reader.enqueue(file, "original", "## Resolution: ADDRESSED\nDone.")
                }

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                }

                val (loop, gitStrategy) = buildWiredLoop(reInstruct, reader)
                loop.execute(buildCtx(feedbackDir, doerCfg, currentIteration = 2))

                gitStrategy.calls shouldHaveSize 2
                gitStrategy.calls.forEach { it.currentIteration shouldBe 2 }
            }
        }
    }

    // ----------------------------------------------------------
    // Scenario 2: Rejection negotiation — reviewer accepts rejection
    // ----------------------------------------------------------

    describe("GIVEN doer rejects a feedback item") {
        describe("WHEN reviewer accepts the rejection (signals PASS)") {

            it("THEN file moves to rejected/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()
                val file = writePendingFile(
                    feedbackDir, "important__unnecessary-check.md",
                    "Add redundant null check.",
                )

                val reader = QueueBasedFeedbackFileReader()
                // InnerFeedbackLoop reads: 1) original content, 2) resolution (REJECTED)
                reader.enqueue(file, "Add redundant null check.", "## Resolution: REJECTED\nNot needed.")
                // RejectionNegotiationUseCaseImpl reads the file for reviewer judgment message
                reader.enqueue(file, "## Resolution: REJECTED\nNot needed.")

                // ReInstruct calls: 1) doer processes item -> COMPLETED, 2) reviewer judges -> PASS
                var callCount = 0
                val reInstruct = ReInstructAndAwait { _, _ ->
                    callCount++
                    when (callCount) {
                        1 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                        2 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.PASS))
                        else -> error("Unexpected call count=[$callCount]")
                    }
                }

                val (loop, gitStrategy) = buildWiredLoop(reInstruct, reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                listFiles(feedbackDir.resolve("rejected")) shouldContainExactly listOf(
                    "important__unnecessary-check.md",
                )
                listFiles(feedbackDir.resolve("pending")) shouldHaveSize 0
            }
        }
    }

    // ----------------------------------------------------------
    // Scenario 3: Rejection insistence — reviewer insists, doer complies
    // ----------------------------------------------------------

    describe("GIVEN doer rejects a feedback item") {
        describe("WHEN reviewer insists and doer complies with ADDRESSED") {

            it("THEN file moves to addressed/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()
                val file = writePendingFile(
                    feedbackDir, "critical__security-fix.md",
                    "Fix security vulnerability.",
                )

                val reader = QueueBasedFeedbackFileReader()
                // InnerFeedbackLoop reads: 1) original content, 2) resolution (REJECTED)
                reader.enqueue(
                    file,
                    "Fix security vulnerability.",
                    "## Resolution: REJECTED\nNot a real issue.",
                )
                // RejectionNegotiation reads: 1) for reviewer judgment, 2) after doer compliance
                reader.enqueue(
                    file,
                    "## Resolution: REJECTED\nNot a real issue.",
                    "## Resolution: ADDRESSED\nFixed reluctantly.",
                )

                // ReInstruct calls:
                // 1) doer processes item -> COMPLETED (with REJECTED resolution)
                // 2) reviewer judges -> NEEDS_ITERATION (insist)
                // 3) doer complies -> COMPLETED (with ADDRESSED resolution)
                var callCount = 0
                val reInstruct = ReInstructAndAwait { _, _ ->
                    callCount++
                    when (callCount) {
                        1 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                        2 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.NEEDS_ITERATION))
                        3 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                        else -> error("Unexpected call count=[$callCount]")
                    }
                }

                val (loop, _) = buildWiredLoop(reInstruct, reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                listFiles(feedbackDir.resolve("addressed")) shouldContainExactly listOf(
                    "critical__security-fix.md",
                )
                listFiles(feedbackDir.resolve("pending")) shouldHaveSize 0
            }
        }
    }

    // ----------------------------------------------------------
    // Scenario 4: Part completion guard — PASS with pending critical
    // ----------------------------------------------------------

    describe("GIVEN critical feedback files remain in pending/") {
        describe("WHEN PartCompletionGuard validates on reviewer PASS") {

            it("THEN guard fails with message about unaddressed items") {
                val feedbackDir = createFeedbackDir()
                writePendingFile(feedbackDir, "critical__still-broken.md")

                val guard = PartCompletionGuard()
                val result = guard.validate(
                    feedbackDir.resolve("pending"),
                    feedbackDir.resolve("addressed"),
                )

                result.shouldBeInstanceOf<PartCompletionGuard.GuardResult.Failed>()
            }

            it("THEN guard passes when only optional items remain in pending") {
                val feedbackDir = createFeedbackDir()
                writePendingFile(feedbackDir, "optional__minor-style.md")

                val guard = PartCompletionGuard()
                val result = guard.validate(
                    feedbackDir.resolve("pending"),
                    feedbackDir.resolve("addressed"),
                )

                result shouldBe PartCompletionGuard.GuardResult.Passed

                // Optional file should have been moved to addressed/
                listFiles(feedbackDir.resolve("addressed")) shouldContainExactly listOf(
                    "optional__minor-style.md",
                )
                listFiles(feedbackDir.resolve("pending")) shouldHaveSize 0
            }

            it("THEN guard passes when pending is empty") {
                val feedbackDir = createFeedbackDir()

                val guard = PartCompletionGuard()
                val result = guard.validate(
                    feedbackDir.resolve("pending"),
                    feedbackDir.resolve("addressed"),
                )

                result shouldBe PartCompletionGuard.GuardResult.Passed
            }
        }
    }

    // ----------------------------------------------------------
    // Scenario 5: Iteration counter — stays constant per inner loop
    // (Covered in Scenario 1's second `it` block above — iteration 2)
    // ----------------------------------------------------------

    // ----------------------------------------------------------
    // Scenario 6: Feedback files presence guard — empty pending
    // ----------------------------------------------------------

    describe("GIVEN pending/ directory is empty after reviewer signals needs_iteration") {
        describe("WHEN inner feedback loop executes") {

            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()

                val reader = QueueBasedFeedbackFileReader()
                val reInstruct = ReInstructAndAwait { _, _ ->
                    error("Should not be called — loop should fail before processing")
                }

                val (loop, _) = buildWiredLoop(reInstruct, reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult = (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ----------------------------------------------------------
    // Scenario 7: Optional feedback SKIPPED — moves to addressed/
    // ----------------------------------------------------------

    describe("GIVEN an optional feedback file") {
        describe("WHEN doer writes SKIPPED resolution") {

            it("THEN file moves to addressed/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()
                val file = writePendingFile(
                    feedbackDir, "optional__naming-convention.md",
                    "Consider renaming variable.",
                )

                val reader = QueueBasedFeedbackFileReader()
                reader.enqueue(file, "Consider renaming variable.", "## Resolution: SKIPPED\nNot worth it.")

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                }

                val (loop, _) = buildWiredLoop(reInstruct, reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                listFiles(feedbackDir.resolve("addressed")) shouldContainExactly listOf(
                    "optional__naming-convention.md",
                )
            }
        }

        describe("WHEN doer writes SKIPPED on a critical item") {

            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()
                val file = writePendingFile(
                    feedbackDir, "critical__must-fix.md",
                    "Critical issue.",
                )

                val reader = QueueBasedFeedbackFileReader()
                reader.enqueue(file, "Critical issue.", "## Resolution: SKIPPED\nSkipping.")

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                }

                val (loop, _) = buildWiredLoop(reInstruct, reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult = (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ----------------------------------------------------------
    // Scenario: Mixed flow — addressed + rejected + skipped in one loop
    // ----------------------------------------------------------

    describe("GIVEN critical (addressed), important (rejected+accepted), optional (skipped)") {
        describe("WHEN inner loop processes all three") {

            lateinit var feedbackDir: Path
            lateinit var gitStrategy: RecordingGitStrategy
            lateinit var result: InnerLoopOutcome

            beforeEach {
                feedbackDir = createFeedbackDir()
                val doerCfg = createDoerConfigWithPublicMd()

                val criticalFile = writePendingFile(
                    feedbackDir, "critical__real-bug.md", "Fix the bug.",
                )
                val importantFile = writePendingFile(
                    feedbackDir, "important__style-disagree.md", "Rename method.",
                )
                val optionalFile = writePendingFile(
                    feedbackDir, "optional__minor.md", "Minor nit.",
                )

                val reader = QueueBasedFeedbackFileReader()
                // Critical: read original, then ADDRESSED
                reader.enqueue(criticalFile, "Fix the bug.", "## Resolution: ADDRESSED\nFixed.")
                // Important: read original, then REJECTED (triggers negotiation)
                reader.enqueue(importantFile, "Rename method.", "## Resolution: REJECTED\nDisagree.")
                // RejectionNegotiation reads the file for reviewer judgment
                reader.enqueue(importantFile, "## Resolution: REJECTED\nDisagree.")
                // Optional: read original, then SKIPPED
                reader.enqueue(optionalFile, "Minor nit.", "## Resolution: SKIPPED\nNot worth it.")

                // ReInstruct calls:
                // 1) doer processes critical -> COMPLETED
                // 2) doer processes important -> COMPLETED (REJECTED)
                // 3) reviewer judges important rejection -> PASS (accepts)
                // 4) doer processes optional -> COMPLETED (SKIPPED)
                var callCount = 0
                val reInstruct = ReInstructAndAwait { _, _ ->
                    callCount++
                    when (callCount) {
                        1 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                        2 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                        3 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.PASS))
                        4 -> ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
                        else -> error("Unexpected call count=[$callCount]")
                    }
                }

                val setup = buildWiredLoop(reInstruct, reader)
                gitStrategy = setup.gitStrategy
                result = setup.loop.execute(buildCtx(feedbackDir, doerCfg))
            }

            it("THEN result is Continue") {
                result shouldBe InnerLoopOutcome.Continue
            }

            it("THEN critical and optional land in addressed/") {
                listFiles(feedbackDir.resolve("addressed")) shouldContainExactly listOf(
                    "critical__real-bug.md",
                    "optional__minor.md",
                )
            }

            it("THEN rejected important item lands in rejected/") {
                listFiles(feedbackDir.resolve("rejected")) shouldContainExactly listOf(
                    "important__style-disagree.md",
                )
            }

            it("THEN pending/ is empty") {
                listFiles(feedbackDir.resolve("pending")) shouldHaveSize 0
            }

            it("THEN git commit is called once per feedback item") {
                gitStrategy.calls shouldHaveSize 3
            }
        }
    }
})
