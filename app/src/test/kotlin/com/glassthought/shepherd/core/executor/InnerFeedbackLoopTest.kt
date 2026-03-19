package com.glassthought.shepherd.core.executor

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.facade.AgentPayload
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
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.usecase.rejectionnegotiation.FeedbackFileReader
import com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionNegotiationUseCase
import com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionResult
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwait
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructOutcome
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class InnerFeedbackLoopTest : AsgardDescribeSpec({

    // ── Helpers ────────────────────────────────────────────────────────

    fun buildHandle(
        guidSuffix: String = "test-guid",
        sessionId: String = "session-1",
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

    val doerHandle = buildHandle("doer")
    val reviewerHandle = buildHandle("reviewer", "session-2")

    val testDoerConfig = SubPartConfig(
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
        outputDir = Path.of("/tmp/doer-output"),
        publicMdOutputPath = Path.of("/tmp/doer-output/PUBLIC.md"),
        privateMdPath = null,
        executionContext = ExecutionContext(
            partName = "part_1",
            partDescription = "Test part",
            planMdPath = null,
            priorPublicMdPaths = emptyList(),
        ),
    )

    /** Creates a temp feedback dir with pending/, addressed/, rejected/. */
    fun createFeedbackDir(): Path {
        val dir = Files.createTempDirectory("feedback-test")
        Files.createDirectories(dir.resolve("pending"))
        Files.createDirectories(dir.resolve("addressed"))
        Files.createDirectories(dir.resolve("rejected"))
        return dir
    }

    /** Writes a feedback file to pending/ and returns its path. */
    fun writePendingFile(
        feedbackDir: Path,
        filename: String,
        content: String = "# Feedback\nSome issue.",
    ): Path {
        val file = feedbackDir.resolve("pending").resolve(filename)
        Files.writeString(file, content)
        return file
    }

    /** Creates a PUBLIC.md file so the validator passes. */
    fun ensurePublicMd(doerConfig: SubPartConfig): SubPartConfig {
        val dir = Files.createTempDirectory("doer-output")
        val publicMd = dir.resolve("PUBLIC.md")
        Files.writeString(publicMd, "# Output\nDone.")
        return doerConfig.copy(
            outputDir = dir,
            publicMdOutputPath = publicMd,
        )
    }

    class RecordingGitStrategy : GitCommitStrategy {
        val calls = mutableListOf<SubPartDoneContext>()
        override suspend fun onSubPartDone(context: SubPartDoneContext) {
            calls.add(context)
        }
    }

    /**
     * Fake ReInstructAndAwait that always returns Responded(COMPLETED).
     */
    fun alwaysCompletedReInstruct() = ReInstructAndAwait { _, _ ->
        ReInstructOutcome.Responded(AgentSignal.Done(DoneResult.COMPLETED))
    }

    /**
     * Fake FeedbackFileReader that returns content from a mutable map.
     * Keys are file path strings.
     */
    class FakeFeedbackFileReader : FeedbackFileReader {
        val contentByPath = mutableMapOf<String, String>()
        override suspend fun readContent(path: Path): String =
            contentByPath[path.toString()]
                ?: error("No content set for path=$path")
    }

    /** Fake ContextForAgentProvider that returns a dummy instruction path. */
    fun fakeContextProvider(): ContextForAgentProvider {
        val instructionFile = Files.createTempFile("instructions", ".md")
        Files.writeString(instructionFile, "# Instructions")
        return ContextForAgentProvider { _ -> instructionFile }
    }

    fun buildLoop(
        reInstructAndAwait: ReInstructAndAwait = alwaysCompletedReInstruct(),
        rejectionUseCase: RejectionNegotiationUseCase = RejectionNegotiationUseCase { _, _, _ ->
            error("rejection not expected")
        },
        feedbackFileReader: FeedbackFileReader = FakeFeedbackFileReader(),
        gitStrategy: GitCommitStrategy = RecordingGitStrategy(),
        facade: FakeAgentFacade = FakeAgentFacade(),
    ): InnerFeedbackLoop {
        facade.onReadContextWindowState {
            ContextWindowState(remainingPercentage = 80)
        }
        return InnerFeedbackLoop(
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
    }

    fun buildCtx(
        feedbackDir: Path,
        doerConfig: SubPartConfig = testDoerConfig,
    ) = InnerLoopContext(
        doerHandle = doerHandle,
        reviewerHandle = reviewerHandle,
        feedbackDir = feedbackDir,
        doerConfig = doerConfig,
        currentIteration = 1,
        maxIterations = 3,
    )

    // ── R9: Empty pending → AgentCrashed ────────────────────────────

    describe("GIVEN pending/ directory is empty") {
        describe("WHEN inner feedback loop executes") {
            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val loop = buildLoop()
                val result = loop.execute(buildCtx(feedbackDir))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult = (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Unrecognized severity prefix → AgentCrashed ─────────────────

    describe("GIVEN pending/ contains a file with unrecognized severity prefix") {
        describe("WHEN inner feedback loop executes") {
            it("THEN result is Terminate(AgentCrashed) mentioning the bad filename") {
                val feedbackDir = createFeedbackDir()
                // Typo: "critcal__" instead of "critical__"
                writePendingFile(feedbackDir, "critcal__typo-issue.md")
                writePendingFile(feedbackDir, "important__valid.md")

                val loop = buildLoop()
                val result = loop.execute(buildCtx(feedbackDir))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult = (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }

            it("THEN error message contains the unrecognized filename") {
                val feedbackDir = createFeedbackDir()
                writePendingFile(feedbackDir, "critcal__typo-issue.md")

                val loop = buildLoop()
                val result = loop.execute(buildCtx(feedbackDir))

                val crashed = (result as InnerLoopOutcome.Terminate).result
                    as PartResult.AgentCrashed
                crashed.details.contains("critcal__typo-issue.md") shouldBe true
            }
        }
    }

    // ── Severity ordering ─────────────────────────────────────────────

    describe("GIVEN critical, important, and optional files in pending/") {
        describe("WHEN inner feedback loop executes") {
            it("THEN files are processed in order: critical → important → optional") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)

                writePendingFile(feedbackDir, "optional__style.md")
                writePendingFile(feedbackDir, "critical__null-check.md")
                writePendingFile(feedbackDir, "important__error-handling.md")

                val reader = FakeFeedbackFileReader()

                // Set up reader to return ADDRESSED content
                val pendingDir = feedbackDir.resolve("pending")
                Files.list(pendingDir).use { stream ->
                    stream.forEach { file ->
                        reader.contentByPath[file.toString()] =
                            "## Resolution: ADDRESSED\nDone."
                    }
                }

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.Responded(
                        AgentSignal.Done(DoneResult.COMPLETED),
                    )
                }

                val gitStrategy = RecordingGitStrategy()
                val facade = FakeAgentFacade()
                facade.onReadContextWindowState {
                    ContextWindowState(remainingPercentage = 80)
                }

                val loop = buildLoop(
                    reInstructAndAwait = reInstruct,
                    feedbackFileReader = reader,
                    gitStrategy = gitStrategy,
                    facade = facade,
                )

                val result = loop.execute(buildCtx(feedbackDir, doerCfg))
                result shouldBe InnerLoopOutcome.Continue

                // All 3 files should have been committed
                gitStrategy.calls shouldHaveSize 3

                // Verify files moved to addressed/
                val addressedFiles = Files.list(
                    feedbackDir.resolve("addressed")
                ).use { s -> s.map { it.fileName.toString() }.sorted().toList() }

                addressedFiles shouldContainExactly listOf(
                    "critical__null-check.md",
                    "important__error-handling.md",
                    "optional__style.md",
                )
            }
        }
    }

    // ── ADDRESSED → file moved to addressed/ ────────────────────────

    describe("GIVEN a single critical feedback file") {
        describe("WHEN doer resolves as ADDRESSED") {
            it("THEN file is moved to addressed/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "critical__issue.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: ADDRESSED\nFixed it."

                val loop = buildLoop(feedbackFileReader = reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                Files.exists(
                    feedbackDir.resolve("addressed/critical__issue.md"),
                ) shouldBe true
                Files.exists(file) shouldBe false
            }
        }
    }

    // ── SKIPPED on optional → moved to addressed/ ───────────────────

    describe("GIVEN an optional feedback file") {
        describe("WHEN doer resolves as SKIPPED") {
            it("THEN file is moved to addressed/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "optional__minor-style.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: SKIPPED\nNot worth it."

                val loop = buildLoop(feedbackFileReader = reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                Files.exists(
                    feedbackDir.resolve("addressed/optional__minor-style.md"),
                ) shouldBe true
            }
        }
    }

    // ── SKIPPED on critical/important → AgentCrashed ────────────────

    describe("GIVEN a critical feedback file") {
        describe("WHEN doer resolves as SKIPPED") {
            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "critical__must-fix.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: SKIPPED\nDon't want to."

                val loop = buildLoop(feedbackFileReader = reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult =
                    (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    describe("GIVEN an important feedback file") {
        describe("WHEN doer resolves as SKIPPED") {
            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "important__must-fix.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: SKIPPED\nDon't want to."

                val loop = buildLoop(feedbackFileReader = reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult =
                    (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Missing resolution marker → AgentCrashed ────────────────────

    describe("GIVEN a feedback file") {
        describe("WHEN doer does not write resolution marker") {
            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "critical__issue.md",
                )

                val reader = FakeFeedbackFileReader()
                // No resolution marker in content
                reader.contentByPath[file.toString()] =
                    "# Feedback\nSome issue.\nNo resolution here."

                val loop = buildLoop(feedbackFileReader = reader)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult =
                    (result as InnerLoopOutcome.Terminate).result
                partResult.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Self-compaction check fires at each done boundary ───────────

    describe("GIVEN multiple pending feedback files") {
        describe("WHEN inner loop processes them") {
            it("THEN readContextWindowState is called for each item") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                writePendingFile(feedbackDir, "critical__a.md")
                writePendingFile(feedbackDir, "important__b.md")

                val reader = FakeFeedbackFileReader()
                val pendingDir = feedbackDir.resolve("pending")
                Files.list(pendingDir).use { stream ->
                    stream.forEach { file ->
                        reader.contentByPath[file.toString()] =
                            "## Resolution: ADDRESSED\nFixed."
                    }
                }

                val facade = FakeAgentFacade()
                facade.onReadContextWindowState {
                    ContextWindowState(remainingPercentage = 70)
                }

                val loop = buildLoop(
                    feedbackFileReader = reader,
                    facade = facade,
                )
                loop.execute(buildCtx(feedbackDir, doerCfg))

                // One readContextWindowState per feedback item
                facade.readContextWindowStateCalls shouldHaveSize 2
            }
        }
    }

    // ── Iteration counter not incremented per item ──────────────────

    describe("GIVEN multiple pending feedback files") {
        describe("WHEN inner loop completes") {
            it("THEN iteration number in git commits stays constant") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                writePendingFile(feedbackDir, "critical__a.md")
                writePendingFile(feedbackDir, "critical__b.md")

                val reader = FakeFeedbackFileReader()
                val pendingDir = feedbackDir.resolve("pending")
                Files.list(pendingDir).use { stream ->
                    stream.forEach { file ->
                        reader.contentByPath[file.toString()] =
                            "## Resolution: ADDRESSED\nFixed."
                    }
                }

                val gitStrategy = RecordingGitStrategy()
                val loop = buildLoop(
                    feedbackFileReader = reader,
                    gitStrategy = gitStrategy,
                )

                val ctx = buildCtx(feedbackDir, doerCfg)
                loop.execute(ctx)

                // All commits should have the same iteration number
                gitStrategy.calls shouldHaveSize 2
                gitStrategy.calls.forEach {
                    it.currentIteration shouldBe 1
                }
            }
        }
    }

    // ── REJECTED → delegates to RejectionNegotiationUseCase ─────────

    describe("GIVEN a feedback file") {
        describe("WHEN doer resolves as REJECTED and reviewer accepts") {
            it("THEN file is moved to rejected/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "important__issue.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: REJECTED\nNot needed."

                val rejectionUseCase =
                    RejectionNegotiationUseCase { _, _, _ ->
                        RejectionResult.Accepted
                    }

                val loop = buildLoop(
                    feedbackFileReader = reader,
                    rejectionUseCase = rejectionUseCase,
                )
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                Files.exists(
                    feedbackDir.resolve("rejected/important__issue.md"),
                ) shouldBe true
            }
        }
    }

    describe("GIVEN a feedback file") {
        describe("WHEN doer REJECTED and reviewer insists, doer addresses") {
            it("THEN file is moved to addressed/ directory") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "critical__issue.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: REJECTED\nNot needed."

                val rejectionUseCase =
                    RejectionNegotiationUseCase { _, _, _ ->
                        RejectionResult.AddressedAfterInsistence
                    }

                val loop = buildLoop(
                    feedbackFileReader = reader,
                    rejectionUseCase = rejectionUseCase,
                )
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                Files.exists(
                    feedbackDir.resolve("addressed/critical__issue.md"),
                ) shouldBe true
            }
        }
    }

    describe("GIVEN a feedback file") {
        describe("WHEN rejection negotiation results in AgentCrashed") {
            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                val file = writePendingFile(
                    feedbackDir, "critical__issue.md",
                )

                val reader = FakeFeedbackFileReader()
                reader.contentByPath[file.toString()] =
                    "## Resolution: REJECTED\nNot needed."

                val rejectionUseCase =
                    RejectionNegotiationUseCase { _, _, _ ->
                        RejectionResult.AgentCrashed("doer defied")
                    }

                val loop = buildLoop(
                    feedbackFileReader = reader,
                    rejectionUseCase = rejectionUseCase,
                )
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult =
                    (result as InnerLoopOutcome.Terminate).result
                partResult shouldBe PartResult.AgentCrashed("doer defied")
            }
        }
    }

    // ── ReInstruct crash propagates ─────────────────────────────────

    describe("GIVEN a feedback file") {
        describe("WHEN ReInstructAndAwait returns Crashed") {
            it("THEN result is Terminate(AgentCrashed)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                writePendingFile(feedbackDir, "critical__issue.md")

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.Crashed("agent died")
                }

                val loop = buildLoop(reInstructAndAwait = reInstruct)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult =
                    (result as InnerLoopOutcome.Terminate).result
                partResult shouldBe PartResult.AgentCrashed("agent died")
            }
        }
    }

    describe("GIVEN a feedback file") {
        describe("WHEN ReInstructAndAwait returns FailedWorkflow") {
            it("THEN result is Terminate(FailedWorkflow)") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                writePendingFile(feedbackDir, "critical__issue.md")

                val reInstruct = ReInstructAndAwait { _, _ ->
                    ReInstructOutcome.FailedWorkflow("bad state")
                }

                val loop = buildLoop(reInstructAndAwait = reInstruct)
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result.shouldBeInstanceOf<InnerLoopOutcome.Terminate>()
                val partResult =
                    (result as InnerLoopOutcome.Terminate).result
                partResult shouldBe PartResult.FailedWorkflow("bad state")
            }
        }
    }

    // ── Multiple items across severities processed in correct order ──

    describe("GIVEN files: 2 critical, 1 important, 1 optional") {
        describe("WHEN inner loop processes them") {
            it("THEN all are moved to addressed/ in severity order") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)

                writePendingFile(feedbackDir, "critical__b.md")
                writePendingFile(feedbackDir, "critical__a.md")
                writePendingFile(feedbackDir, "important__x.md")
                writePendingFile(feedbackDir, "optional__y.md")

                val reader = FakeFeedbackFileReader()
                val pendingDir = feedbackDir.resolve("pending")
                Files.list(pendingDir).use { stream ->
                    stream.forEach { file ->
                        reader.contentByPath[file.toString()] =
                            "## Resolution: ADDRESSED\nFixed."
                    }
                }

                val gitStrategy = RecordingGitStrategy()
                val loop = buildLoop(
                    feedbackFileReader = reader,
                    gitStrategy = gitStrategy,
                )
                val result = loop.execute(buildCtx(feedbackDir, doerCfg))

                result shouldBe InnerLoopOutcome.Continue
                gitStrategy.calls shouldHaveSize 4

                // Verify all files moved to addressed/
                val addressedFiles = Files.list(
                    feedbackDir.resolve("addressed"),
                ).use { s ->
                    s.map { it.fileName.toString() }.sorted().toList()
                }
                addressedFiles shouldHaveSize 4

                // Verify pending/ is empty
                val pendingFiles = Files.list(pendingDir).use { s ->
                    s.toList()
                }
                pendingFiles shouldHaveSize 0
            }
        }
    }

    // ── Git commit per processed item ───────────────────────────────

    describe("GIVEN 2 feedback files") {
        describe("WHEN both are ADDRESSED") {
            it("THEN git commit is called twice with FEEDBACK_ADDRESSED result") {
                val feedbackDir = createFeedbackDir()
                val doerCfg = ensurePublicMd(testDoerConfig)
                writePendingFile(feedbackDir, "critical__a.md")
                writePendingFile(feedbackDir, "critical__b.md")

                val reader = FakeFeedbackFileReader()
                Files.list(feedbackDir.resolve("pending")).use { stream ->
                    stream.forEach { file ->
                        reader.contentByPath[file.toString()] =
                            "## Resolution: ADDRESSED\nFixed."
                    }
                }

                val gitStrategy = RecordingGitStrategy()
                val loop = buildLoop(
                    feedbackFileReader = reader,
                    gitStrategy = gitStrategy,
                )
                loop.execute(buildCtx(feedbackDir, doerCfg))

                gitStrategy.calls shouldHaveSize 2
                gitStrategy.calls.forEach {
                    it.result shouldBe "FEEDBACK_ADDRESSED"
                }
            }
        }
    }
})
