package com.glassthought.shepherd.core.executor

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.facade.AgentPayload
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.ContextWindowState
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.facade.FakeAgentFacade
import com.glassthought.shepherd.core.agent.facade.SpawnAgentConfig
import com.glassthought.shepherd.core.agent.facade.SpawnedAgentHandle
import com.glassthought.shepherd.core.agent.rolecatalog.RoleDefinition
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.agent.sessionresolver.ResumableAgentSessionId
import com.glassthought.shepherd.core.compaction.SelfCompactionInstructionBuilder
import com.glassthought.shepherd.core.context.AgentInstructionRequest
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.context.ExecutionContext
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.data.HarnessTimeoutConfig
import com.glassthought.shepherd.core.state.IterationConfig
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.SubPartRole
import com.glassthought.shepherd.core.supporting.git.GitCommitStrategy
import com.glassthought.shepherd.core.supporting.git.SubPartDoneContext
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToConvergeUseCase
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructAndAwait
import com.glassthought.shepherd.usecase.reinstructandawait.ReInstructOutcome
import com.glassthought.shepherd.usecase.rejectionnegotiation.FeedbackFileReader
import com.glassthought.shepherd.usecase.rejectionnegotiation.RejectionNegotiationUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class PartExecutorImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

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

    val testRoleDefinition = RoleDefinition(
        name = "DOER",
        description = "test doer role",
        descriptionLong = null,
        filePath = Path.of("/tmp/roles/doer.md"),
    )

    val testReviewerRoleDefinition = RoleDefinition(
        name = "REVIEWER",
        description = "test reviewer role",
        descriptionLong = null,
        filePath = Path.of("/tmp/roles/reviewer.md"),
    )

    val testExecutionContext = ExecutionContext(
        partName = "part_1",
        partDescription = "Test part",
        planMdPath = null,
        priorPublicMdPaths = emptyList(),
    )

    /**
     * Creates a temp directory with a PUBLIC.md file containing content.
     * Returns the path to the PUBLIC.md file.
     */
    fun createPublicMdFile(content: String = "# Test Output\nSome content"): Path {
        val dir = Files.createTempDirectory("executor-test")
        val publicMd = dir.resolve("PUBLIC.md")
        Files.writeString(publicMd, content)
        return publicMd
    }

    fun buildDoerConfig(publicMdPath: Path, privateMdPath: Path? = null): SubPartConfig = SubPartConfig(
        partName = "part_1",
        subPartName = "doer",
        subPartIndex = 0,
        subPartRole = SubPartRole.DOER,
        agentType = AgentType.CLAUDE_CODE,
        model = "test-model",
        systemPromptPath = Path.of("/tmp/prompt.md"),
        bootstrapMessage = "bootstrap",
        roleDefinition = testRoleDefinition,
        ticketContent = "Test ticket",
        outputDir = publicMdPath.parent,
        publicMdOutputPath = publicMdPath,
        privateMdPath = privateMdPath,
        executionContext = testExecutionContext,
    )

    fun buildReviewerConfig(
        publicMdPath: Path,
        feedbackDir: Path? = null,
        privateMdPath: Path? = null,
    ): SubPartConfig = SubPartConfig(
        partName = "part_1",
        subPartName = "reviewer",
        subPartIndex = 1,
        subPartRole = SubPartRole.REVIEWER,
        agentType = AgentType.CLAUDE_CODE,
        model = "test-model",
        systemPromptPath = Path.of("/tmp/prompt.md"),
        bootstrapMessage = "bootstrap",
        roleDefinition = testReviewerRoleDefinition,
        ticketContent = "Test ticket",
        outputDir = publicMdPath.parent,
        publicMdOutputPath = publicMdPath,
        privateMdPath = privateMdPath,
        executionContext = testExecutionContext,
        doerPublicMdPath = Path.of("/tmp/doer/PUBLIC.md"),
        feedbackDir = feedbackDir ?: publicMdPath.parent.resolve("__feedback"),
    )

    /** Fake ContextForAgentProvider that returns a dummy instruction path. */
    fun fakeContextProvider(): ContextForAgentProvider {
        val instructionFile = Files.createTempFile("instructions", ".md")
        Files.writeString(instructionFile, "# Instructions")
        return ContextForAgentProvider { _ -> instructionFile }
    }

    /** Records git commit calls for verification. */
    class RecordingGitCommitStrategy : GitCommitStrategy {
        val calls = mutableListOf<SubPartDoneContext>()
        override suspend fun onSubPartDone(context: SubPartDoneContext) {
            calls.add(context)
        }
    }

    /** Always returns false (abort). */
    val abortingFailedToConverge = FailedToConvergeUseCase { _, _ -> false }

    /** Always returns true (grant more). */
    val grantingFailedToConverge = FailedToConvergeUseCase { _, _ -> true }

    fun buildExecutor(
        doerConfig: SubPartConfig,
        reviewerConfig: SubPartConfig? = null,
        facade: FakeAgentFacade,
        contextProvider: ContextForAgentProvider = fakeContextProvider(),
        gitCommitStrategy: GitCommitStrategy = RecordingGitCommitStrategy(),
        failedToConvergeUseCase: FailedToConvergeUseCase = abortingFailedToConverge,
        iterationConfig: IterationConfig = IterationConfig(max = 3),
        harnessTimeoutConfig: HarnessTimeoutConfig = HarnessTimeoutConfig.defaults(),
    ): PartExecutorImpl = PartExecutorImpl(
        doerConfig = doerConfig,
        reviewerConfig = reviewerConfig,
        deps = PartExecutorDeps(
            agentFacade = facade,
            contextForAgentProvider = contextProvider,
            gitCommitStrategy = gitCommitStrategy,
            failedToConvergeUseCase = failedToConvergeUseCase,
            outFactory = outFactory,
            harnessTimeoutConfig = harnessTimeoutConfig,
        ),
        iterationConfig = iterationConfig,
    )

    // ── Doer-only: Happy path ──────────────────────────────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Done(COMPLETED) and PUBLIC.md exists") {
            val publicMd = createPublicMdFile()
            val doerConfig = buildDoerConfig(publicMd)
            val doerHandle = buildHandle("doer")
            val facade = FakeAgentFacade()
            facade.onSpawn { doerHandle }
            facade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
            facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

            val gitStrategy = RecordingGitCommitStrategy()
            val executor = buildExecutor(doerConfig, facade = facade, gitCommitStrategy = gitStrategy)

            it("THEN the result is PartResult.Completed") {
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }
        }
    }

    // ── Doer-only: Git commit is called ─────────────────────────────────

    describe("GIVEN a doer-only executor with recording git strategy") {
        describe("WHEN the doer signals Done(COMPLETED)") {
            val publicMd = createPublicMdFile()
            val doerConfig = buildDoerConfig(publicMd)
            val doerHandle = buildHandle("doer")
            val facade = FakeAgentFacade()
            facade.onSpawn { doerHandle }
            facade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
            facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

            val gitStrategy = RecordingGitCommitStrategy()
            val executor = buildExecutor(doerConfig, facade = facade, gitCommitStrategy = gitStrategy)

            it("THEN git commit strategy is called once") {
                executor.execute()
                gitStrategy.calls shouldHaveSize 1
            }

            it("THEN git commit context has correct part name") {
                gitStrategy.calls.first().partName shouldBe "part_1"
            }
        }
    }

    // ── Doer-only: FailWorkflow ─────────────────────────────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals FailWorkflow") {

            it("THEN the result is PartResult.FailedWorkflow with the reason") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.FailWorkflow("missing dependency")
                }
                val executor = buildExecutor(doerConfig, facade = facade)

                val result = executor.execute()
                result shouldBe PartResult.FailedWorkflow("missing dependency")
            }
        }
    }

    // ── Doer-only: Crashed ──────────────────────────────────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Crashed") {

            it("THEN the result is PartResult.AgentCrashed with the details") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Crashed("agent unresponsive")
                }
                val executor = buildExecutor(doerConfig, facade = facade)

                val result = executor.execute()
                result shouldBe PartResult.AgentCrashed("agent unresponsive")
            }
        }
    }

    // ── Doer-only: Done(PASS) → IllegalStateException ───────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Done(PASS)") {

            it("THEN IllegalStateException is thrown") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.PASS)
                }
                val executor = buildExecutor(doerConfig, facade = facade)

                shouldThrow<IllegalStateException> {
                    executor.execute()
                }
            }
        }
    }

    // ── Doer-only: Done(NEEDS_ITERATION) → IllegalStateException ────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Done(NEEDS_ITERATION)") {

            it("THEN IllegalStateException is thrown") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.NEEDS_ITERATION)
                }
                val executor = buildExecutor(doerConfig, facade = facade)

                shouldThrow<IllegalStateException> {
                    executor.execute()
                }
            }
        }
    }

    // ── Doer-only: Missing PUBLIC.md → AgentCrashed ─────────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Done(COMPLETED) but PUBLIC.md does not exist") {

            it("THEN the result is PartResult.AgentCrashed") {
                val nonExistentPath = Path.of("/tmp/non-existent-${System.nanoTime()}/PUBLIC.md")
                val doerConfig = buildDoerConfig(nonExistentPath)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }
                val executor = buildExecutor(doerConfig, facade = facade)

                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Doer-only: Empty PUBLIC.md → AgentCrashed ───────────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Done(COMPLETED) but PUBLIC.md is empty") {

            it("THEN the result is PartResult.AgentCrashed") {
                val dir = Files.createTempDirectory("executor-test-empty")
                val emptyPublicMd = dir.resolve("PUBLIC.md")
                Files.writeString(emptyPublicMd, "")
                val doerConfig = buildDoerConfig(emptyPublicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }
                val executor = buildExecutor(doerConfig, facade = facade)

                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Doer-only: Context window read at done boundary ─────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN the doer signals Done(COMPLETED)") {

            it("THEN readContextWindowState is called") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 60) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                facade.readContextWindowStateCalls shouldHaveSize 1
            }
        }
    }

    // ── Doer-only: Both sessions killed on completion ───────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN execution completes") {

            it("THEN killSession is called for the doer") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val doerHandle = buildHandle("doer")
                val facade = FakeAgentFacade()
                facade.onSpawn { doerHandle }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                facade.killSessionCalls shouldHaveSize 1
                facade.killSessionCalls.first() shouldBe doerHandle
            }
        }
    }

    // ── Doer+Reviewer: Happy path (doer COMPLETED → reviewer PASS) ─────

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN doer signals COMPLETED and reviewer signals PASS") {

            it("THEN the result is PartResult.Completed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),   // doer
                        AgentSignal.Done(DoneResult.PASS),         // reviewer
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(
                    doerConfig, reviewerConfig = reviewerCfg, facade = facade
                )
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }
        }
    }

    // ── Doer+Reviewer: Both sessions killed on completion ───────────────

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN the part completes successfully") {

            it("THEN both doer and reviewer sessions are killed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(
                    doerConfig, reviewerConfig = reviewerCfg, facade = facade
                )
                executor.execute()

                facade.killSessionCalls shouldHaveSize 2
            }
        }
    }

    // ── Doer+Reviewer: Iteration (NEEDS_ITERATION → reviewer re-instruction → PASS) ──
    // NOTE: After NEEDS_ITERATION, the outer loop re-instructs the doer before the reviewer.
    // When innerFeedbackLoop is null, the inner loop is skipped.
    // Flow: doer COMPLETED -> reviewer NEEDS_ITERATION -> doer COMPLETED -> reviewer PASS (4 signals).

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN reviewer sends NEEDS_ITERATION then PASS on next round") {

            it("THEN the result is PartResult.Completed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val gitStrategy = RecordingGitCommitStrategy()
                val executor = buildExecutor(
                    doerConfig,
                    reviewerConfig = reviewerCfg,
                    facade = facade,
                    gitCommitStrategy = gitStrategy,
                )
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }

            it("THEN git commit is called for each Done signal") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val gitStrategy = RecordingGitCommitStrategy()
                val executor = buildExecutor(
                    doerConfig,
                    reviewerConfig = reviewerCfg,
                    facade = facade,
                    gitCommitStrategy = gitStrategy,
                )
                executor.execute()

                // 4 Done signals = 4 git commits (doer COMPLETED, reviewer NEEDS_ITERATION,
                // doer COMPLETED re-instructed, reviewer PASS).
                gitStrategy.calls shouldHaveSize 4
            }
        }
    }

    // ── Doer+Reviewer: Iteration budget exceeded → FailedToConverge ─────

    describe("GIVEN a doer+reviewer executor with max=1 iterations") {
        describe("WHEN reviewer keeps sending NEEDS_ITERATION and operator aborts") {

            it("THEN the result is PartResult.FailedToConverge") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer → budget exceeded
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val executor = buildExecutor(
                    doerConfig,
                    reviewerConfig = reviewerCfg,
                    facade = facade,
                    failedToConvergeUseCase = abortingFailedToConverge,
                    iterationConfig = IterationConfig(max = 1),
                )
                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.FailedToConverge>()
            }
        }
    }

    // ── Doer+Reviewer: Budget exceeded but operator grants more → continues ──

    describe("GIVEN a doer+reviewer executor with max=1 and granting FailedToConvergeUseCase") {
        describe("WHEN reviewer sends NEEDS_ITERATION, operator grants more, then reviewer PASS") {

            it("THEN the result is PartResult.Completed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0 → budget exceeded
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1 (after budget extension)
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val executor = buildExecutor(
                    doerConfig,
                    reviewerConfig = reviewerCfg,
                    facade = facade,
                    failedToConvergeUseCase = grantingFailedToConverge,
                    iterationConfig = IterationConfig(max = 1),
                )
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }
        }
    }

    // ── Doer+Reviewer: Doer FailWorkflow ────────────────────────────────

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN the doer signals FailWorkflow") {

            it("THEN the result is PartResult.FailedWorkflow") {
                val doerPublicMd = createPublicMdFile()
                val reviewerPublicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.FailWorkflow("cannot proceed")
                }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result shouldBe PartResult.FailedWorkflow("cannot proceed")
            }
        }
    }

    // ── Doer+Reviewer: Reviewer spawned lazily (only after doer COMPLETED) ──

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN the doer signals FailWorkflow before reviewer is needed") {

            it("THEN only 1 spawn call is made (doer only, reviewer is not spawned)") {
                val doerPublicMd = createPublicMdFile()
                val reviewerPublicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(
                    listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2"))
                )
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.FailWorkflow("cannot proceed")
                }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                executor.execute()

                facade.spawnCalls shouldHaveSize 1
            }
        }
    }

    // ── Doer+Reviewer: Reviewer Crashed ─────────────────────────────────

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN the reviewer signals Crashed") {

            it("THEN the result is PartResult.AgentCrashed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED), // doer
                        AgentSignal.Crashed("reviewer died"),   // reviewer
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result shouldBe PartResult.AgentCrashed("reviewer died")
            }
        }
    }

    // ── Doer+Reviewer: Missing reviewer PUBLIC.md after PASS → AgentCrashed ──

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN reviewer signals PASS but reviewer PUBLIC.md does not exist") {

            it("THEN the result is PartResult.AgentCrashed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val nonExistentReviewerMd = Path.of("/tmp/non-existent-${System.nanoTime()}/PUBLIC.md")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(nonExistentReviewerMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Doer+Reviewer: Missing doer PUBLIC.md after COMPLETED → AgentCrashed ──

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN doer signals COMPLETED but doer PUBLIC.md does not exist") {

            it("THEN the result is PartResult.AgentCrashed") {
                val nonExistentDoerMd = Path.of("/tmp/non-existent-${System.nanoTime()}/PUBLIC.md")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(nonExistentDoerMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Doer+Reviewer: Context window read at each done boundary ────────

    describe("GIVEN a doer+reviewer executor with iteration") {
        describe("WHEN doer COMPLETED -> reviewer NEEDS_ITERATION -> doer COMPLETED -> reviewer PASS") {

            it("THEN readContextWindowState is called 4 times (once per Done signal)") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(
                    listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")),
                )
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                executor.execute()

                // 4 Done signals = 4 readContextWindowState calls.
                facade.readContextWindowStateCalls shouldHaveSize 4
            }
        }
    }

    // ── Spawn records the config ────────────────────────────────────────

    describe("GIVEN a doer-only executor") {
        describe("WHEN execute is called") {

            it("THEN spawnAgent is called with the doer's SpawnAgentConfig") {
                val publicMd = createPublicMdFile()
                val doerConfig = buildDoerConfig(publicMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    AgentSignal.Done(DoneResult.COMPLETED)
                }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                facade.spawnCalls shouldHaveSize 1
                facade.spawnCalls.first().partName shouldBe "part_1"
                facade.spawnCalls.first().subPartName shouldBe "doer"
                facade.spawnCalls.first().role shouldBe "DOER"
            }
        }
    }

    // ── Doer+Reviewer: Reviewer Done(COMPLETED) → IllegalStateException ──

    describe("GIVEN a doer+reviewer executor") {
        describe("WHEN reviewer signals Done(COMPLETED) instead of PASS or NEEDS_ITERATION") {

            it("THEN IllegalStateException is thrown") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED), // doer
                        AgentSignal.Done(DoneResult.COMPLETED), // reviewer — invalid
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(
                    listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2"))
                )
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)

                shouldThrow<IllegalStateException> {
                    executor.execute()
                }
            }
        }
    }

    // ── Doer+Reviewer: sendPayload called correct number of times ───────

    describe("GIVEN a doer+reviewer executor with one iteration") {
        describe("WHEN doer COMPLETED -> reviewer NEEDS_ITERATION -> doer COMPLETED -> reviewer PASS") {

            it("THEN sendPayloadAndAwaitSignal is called 4 times") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(
                    listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")),
                )
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val executor = buildExecutor(
                    doerConfig, reviewerConfig = reviewerCfg, facade = facade,
                )
                executor.execute()

                // 4 signals: doer instructions, reviewer instructions,
                // doer re-instructions, reviewer re-instructions.
                facade.sendPayloadCalls shouldHaveSize 4
            }
        }
    }

    // ── Doer+Reviewer with InnerFeedbackLoop wired ─────────────────────
    // Exercises the full path: doer COMPLETED → reviewer NEEDS_ITERATION →
    // inner loop processes feedback → reviewer re-instructed → reviewer PASS → Completed

    describe("GIVEN a doer+reviewer executor with InnerFeedbackLoop wired") {
        describe("WHEN reviewer sends NEEDS_ITERATION and inner loop processes feedback") {

            it("THEN the result is PartResult.Completed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)

                // Create feedback directory with a pending file
                val feedbackDir = Files.createTempDirectory("feedback-integ")
                val pendingDir = feedbackDir.resolve("pending")
                Files.createDirectories(pendingDir)
                Files.createDirectories(feedbackDir.resolve("addressed"))
                val feedbackFile = pendingDir.resolve("critical__test-issue.md")
                Files.writeString(feedbackFile, "# Feedback\nSome issue.")

                val reviewerCfg = buildReviewerConfig(
                    reviewerPublicMd,
                    feedbackDir = feedbackDir,
                )

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                // Signal queue: doer COMPLETED, reviewer NEEDS_ITERATION,
                // doer re-instructed COMPLETED, (inner loop re-instructs doer via
                // ReInstructAndAwait — not through facade), reviewer PASS
                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed by outer loop)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1 after inner loop
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                // FeedbackFileReader that returns ADDRESSED resolution after doer processes
                val feedbackFileReader = FeedbackFileReader { _ ->
                    "## Resolution: ADDRESSED\nFixed the issue."
                }

                val innerLoopGitStrategy = RecordingGitCommitStrategy()

                val innerFeedbackLoop = InnerFeedbackLoop(
                    InnerFeedbackLoopDeps(
                        reInstructAndAwait = ReInstructAndAwait { _, _ ->
                            ReInstructOutcome.Responded(
                                AgentSignal.Done(DoneResult.COMPLETED),
                            )
                        },
                        rejectionNegotiationUseCase = RejectionNegotiationUseCase { _, _, _ ->
                            error("rejection not expected")
                        },
                        contextForAgentProvider = fakeContextProvider(),
                        agentFacade = facade,
                        gitCommitStrategy = innerLoopGitStrategy,
                        publicMdValidator = PublicMdValidator(),
                        feedbackFileReader = feedbackFileReader,
                        outFactory = outFactory,
                    )
                )

                val outerGitStrategy = RecordingGitCommitStrategy()
                val executor = PartExecutorImpl(
                    doerConfig = doerConfig,
                    reviewerConfig = reviewerCfg,
                    deps = PartExecutorDeps(
                        agentFacade = facade,
                        contextForAgentProvider = fakeContextProvider(),
                        gitCommitStrategy = outerGitStrategy,
                        failedToConvergeUseCase = abortingFailedToConverge,
                        outFactory = outFactory,
                        innerFeedbackLoop = innerFeedbackLoop,
                    ),
                    iterationConfig = IterationConfig(max = 3),
                )

                val result = executor.execute()
                result shouldBe PartResult.Completed
            }

            it("THEN inner loop moves feedback file from pending to addressed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)

                val feedbackDir = Files.createTempDirectory("feedback-integ2")
                val pendingDir = feedbackDir.resolve("pending")
                Files.createDirectories(pendingDir)
                Files.createDirectories(feedbackDir.resolve("addressed"))
                val feedbackFile = pendingDir.resolve("important__check.md")
                Files.writeString(feedbackFile, "# Feedback\nCheck this.")

                val reviewerCfg = buildReviewerConfig(
                    reviewerPublicMd,
                    feedbackDir = feedbackDir,
                )

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-2")

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 0
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer iter 0
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer iter 1 (re-instructed)
                        AgentSignal.Done(DoneResult.PASS),            // reviewer iter 1
                    )
                )

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 70) }

                val feedbackFileReader = FeedbackFileReader { _ ->
                    "## Resolution: ADDRESSED\nDone."
                }

                val innerFeedbackLoop = InnerFeedbackLoop(
                    InnerFeedbackLoopDeps(
                        reInstructAndAwait = ReInstructAndAwait { _, _ ->
                            ReInstructOutcome.Responded(
                                AgentSignal.Done(DoneResult.COMPLETED),
                            )
                        },
                        rejectionNegotiationUseCase = RejectionNegotiationUseCase { _, _, _ ->
                            error("rejection not expected")
                        },
                        contextForAgentProvider = fakeContextProvider(),
                        agentFacade = facade,
                        gitCommitStrategy = RecordingGitCommitStrategy(),
                        publicMdValidator = PublicMdValidator(),
                        feedbackFileReader = feedbackFileReader,
                        outFactory = outFactory,
                    )
                )

                val executor = PartExecutorImpl(
                    doerConfig = doerConfig,
                    reviewerConfig = reviewerCfg,
                    deps = PartExecutorDeps(
                        agentFacade = facade,
                        contextForAgentProvider = fakeContextProvider(),
                        gitCommitStrategy = RecordingGitCommitStrategy(),
                        failedToConvergeUseCase = abortingFailedToConverge,
                        outFactory = outFactory,
                        innerFeedbackLoop = innerFeedbackLoop,
                    ),
                    iterationConfig = IterationConfig(max = 3),
                )

                executor.execute()

                // Feedback file should have moved from pending/ to addressed/
                Files.exists(feedbackFile) shouldBe false
                Files.exists(
                    feedbackDir.resolve("addressed/important__check.md"),
                ) shouldBe true
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── Self-Compaction: Done-boundary trigger detection ──────────────
    // ═══════════════════════════════════════════════════════════════════

    // ── Compaction: DONE_BOUNDARY triggers compaction on low context ──

    describe("GIVEN a doer-only executor with low context window remaining") {
        describe("WHEN doer signals Done(COMPLETED) and remaining=20 (below threshold=35)") {

            val dir = Files.createTempDirectory("compaction-test")
            val publicMd = dir.resolve("PUBLIC.md")
            Files.writeString(publicMd, "# Output")
            val privateMd = dir.resolve("PRIVATE.md")
            val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)
            val doerHandle = buildHandle("doer")

            val facade = FakeAgentFacade()
            val gitStrategy = RecordingGitCommitStrategy()

            // First sendPayload: Done(COMPLETED) for the main task
            // Second sendPayload: SelfCompacted for the compaction instruction
            val signalQueue = ArrayDeque(listOf(
                AgentSignal.Done(DoneResult.COMPLETED),
                AgentSignal.SelfCompacted,
            ))
            facade.onSpawn { doerHandle }
            facade.onSendPayloadAndAwaitSignal { _, _ ->
                val signal = signalQueue.removeFirst()
                // Simulate agent writing PRIVATE.md when receiving compaction instruction
                if (signal == AgentSignal.SelfCompacted) {
                    Files.writeString(privateMd, "# Compacted context summary")
                }
                signal
            }
            facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 20) }

            val executor = buildExecutor(
                doerConfig, facade = facade, gitCommitStrategy = gitStrategy,
            )

            it("THEN the result is PartResult.Completed") {
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }

            it("THEN a compaction instruction was sent (2 sendPayload calls total)") {
                facade.sendPayloadCalls shouldHaveSize 2
            }

            it("THEN session is killed after compaction") {
                facade.killSessionCalls shouldHaveSize 1
            }

            it("THEN git commit is called twice (once for done, once for compaction)") {
                gitStrategy.calls shouldHaveSize 2
            }

            it("THEN the second git commit has result=SELF_COMPACTED") {
                gitStrategy.calls[1].result shouldBe "SELF_COMPACTED"
            }
        }
    }

    // ── Compaction: Healthy context — NO compaction ──────────────────

    describe("GIVEN a doer-only executor with healthy context window") {
        describe("WHEN doer signals Done(COMPLETED) and remaining=80 (above threshold=35)") {

            it("THEN NO compaction is triggered and only 1 sendPayload call is made") {
                val publicMd = createPublicMdFile()
                val dir = publicMd.parent
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                facade.sendPayloadCalls shouldHaveSize 1
            }
        }
    }

    // ── Compaction: Stale context (null) — NO compaction, warning logged ──

    describe("GIVEN a doer-only executor with stale context window state") {
        describe("WHEN doer signals Done(COMPLETED) and remainingPercentage is null") {

            it("THEN NO compaction is triggered and only 1 sendPayload call is made").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val publicMd = createPublicMdFile()
                val dir = publicMd.parent
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = null) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                facade.sendPayloadCalls shouldHaveSize 1
            }
        }
    }

    // ── Compaction: Done signal during compaction → AgentCrashed ──────

    describe("GIVEN a doer-only executor with low context") {
        describe("WHEN agent sends Done instead of SelfCompacted during compaction") {

            it("THEN the result is AgentCrashed mentioning protocol violation") {
                val dir = Files.createTempDirectory("compaction-done-violation")
                val publicMd = dir.resolve("PUBLIC.md")
                Files.writeString(publicMd, "# Output")
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)

                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),   // main task done
                    AgentSignal.Done(DoneResult.COMPLETED),   // protocol violation during compaction
                ))

                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 20) }

                val executor = buildExecutor(doerConfig, facade = facade)
                val result = executor.execute()

                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
                (result as PartResult.AgentCrashed).details shouldContain "compaction protocol"
            }
        }
    }

    // ── Compaction: PRIVATE.md missing after SelfCompacted → AgentCrashed ──

    describe("GIVEN a doer-only executor with low context") {
        describe("WHEN agent signals SelfCompacted but PRIVATE.md does not exist") {

            it("THEN the result is AgentCrashed") {
                val dir = Files.createTempDirectory("compaction-missing-private")
                val publicMd = dir.resolve("PUBLIC.md")
                Files.writeString(publicMd, "# Output")
                val privateMd = dir.resolve("PRIVATE.md")
                // Note: NOT creating PRIVATE.md — it should be missing
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)

                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),
                    AgentSignal.SelfCompacted,
                ))

                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 20) }

                val executor = buildExecutor(doerConfig, facade = facade)
                val result = executor.execute()

                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
                (result as PartResult.AgentCrashed).details shouldContain "PRIVATE.md"
            }
        }
    }

    // ── Compaction: PRIVATE.md empty after SelfCompacted → AgentCrashed ──

    describe("GIVEN a doer-only executor with low context") {
        describe("WHEN agent signals SelfCompacted but PRIVATE.md is empty") {

            it("THEN the result is AgentCrashed") {
                val dir = Files.createTempDirectory("compaction-empty-private")
                val publicMd = dir.resolve("PUBLIC.md")
                Files.writeString(publicMd, "# Output")
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)

                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),
                    AgentSignal.SelfCompacted,
                ))

                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    val signal = signalQueue.removeFirst()
                    if (signal == AgentSignal.SelfCompacted) {
                        // Agent writes empty PRIVATE.md
                        Files.writeString(privateMd, "")
                    }
                    signal
                }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 20) }

                val executor = buildExecutor(doerConfig, facade = facade)
                val result = executor.execute()

                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
                (result as PartResult.AgentCrashed).details shouldContain "empty PRIVATE.md"
            }
        }
    }

    // ── Compaction: Timeout → AgentCrashed ──────────────────────────

    describe("GIVEN a doer-only executor with low context") {
        describe("WHEN agent crashes (timeout) during compaction") {

            it("THEN the result is AgentCrashed") {
                val dir = Files.createTempDirectory("compaction-timeout")
                val publicMd = dir.resolve("PUBLIC.md")
                Files.writeString(publicMd, "# Output")
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)

                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),
                    AgentSignal.Crashed("Agent timed out during compaction"),
                ))

                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 20) }

                val executor = buildExecutor(doerConfig, facade = facade)
                val result = executor.execute()

                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
                (result as PartResult.AgentCrashed).details shouldContain "timed out"
            }
        }
    }

    // ── Compaction: Session rotation in doer+reviewer path ───────────

    describe("GIVEN a doer+reviewer executor with doer at low context") {
        describe("WHEN doer low-context compaction -> reviewer NEEDS_ITERATION -> doer respawned -> PASS") {

            val dir = Files.createTempDirectory("compaction-rotation")
            val doerPublicMd = dir.resolve("doer-PUBLIC.md")
            Files.writeString(doerPublicMd, "doer output")
            val doerPrivateMd = dir.resolve("PRIVATE.md")
            val reviewerPublicMd = dir.resolve("reviewer-PUBLIC.md")
            Files.writeString(reviewerPublicMd, "reviewer output")
            val doerConfig = buildDoerConfig(doerPublicMd, privateMdPath = doerPrivateMd)
            val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

            val doerHandle1 = buildHandle("doer-v1")
            val doerHandle2 = buildHandle("doer-v2", sessionId = "session-respawned")
            val reviewerHandle = buildHandle("reviewer", sessionId = "session-reviewer")

            // Signal sequence:
            // 1. Doer Done(COMPLETED) — triggers compaction
            // 2. Doer SelfCompacted — compaction succeeds
            // 3. Reviewer NEEDS_ITERATION — triggers next iteration
            // 4. Doer (respawned) Done(COMPLETED) — healthy context this time
            // 5. Reviewer PASS
            val signalQueue = ArrayDeque(listOf(
                AgentSignal.Done(DoneResult.COMPLETED),       // doer v1
                AgentSignal.SelfCompacted,                    // doer v1 compaction
                AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer
                AgentSignal.Done(DoneResult.COMPLETED),       // doer v2
                AgentSignal.Done(DoneResult.PASS),            // reviewer
            ))

            val facade = FakeAgentFacade()
            val spawnQueue = ArrayDeque(listOf(doerHandle1, reviewerHandle, doerHandle2))
            facade.onSpawn { spawnQueue.removeFirst() }
            facade.onSendPayloadAndAwaitSignal { _, _ ->
                val signal = signalQueue.removeFirst()
                if (signal == AgentSignal.SelfCompacted) {
                    Files.writeString(doerPrivateMd, "# Compacted context from doer v1")
                }
                signal
            }

            // First read: low context (triggers compaction for doer v1)
            // Subsequent reads: healthy context
            var contextReadCount = 0
            facade.onReadContextWindowState {
                contextReadCount++
                if (contextReadCount == 1) ContextWindowState(remainingPercentage = 20)
                else ContextWindowState(remainingPercentage = 80)
            }

            val gitStrategy = RecordingGitCommitStrategy()
            val executor = buildExecutor(
                doerConfig, reviewerConfig = reviewerCfg, facade = facade, gitCommitStrategy = gitStrategy,
            )

            it("THEN the result is PartResult.Completed") {
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }

            it("THEN 3 spawn calls are made (doer v1 + reviewer + doer v2 respawn)") {
                facade.spawnCalls shouldHaveSize 3
            }

            it("THEN 5 sendPayload calls are made") {
                facade.sendPayloadCalls shouldHaveSize 5
            }
        }
    }

    // ── Compaction: Exact threshold boundary (remaining=35) triggers ──

    describe("GIVEN a doer-only executor with context at exact threshold") {
        describe("WHEN remaining=35 equals threshold=35") {

            it("THEN compaction IS triggered (threshold is inclusive)") {
                val dir = Files.createTempDirectory("compaction-exact-threshold")
                val publicMd = dir.resolve("PUBLIC.md")
                Files.writeString(publicMd, "# Output")
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)

                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),
                    AgentSignal.SelfCompacted,
                ))

                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ ->
                    val signal = signalQueue.removeFirst()
                    if (signal == AgentSignal.SelfCompacted) {
                        Files.writeString(privateMd, "# Context summary")
                    }
                    signal
                }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 35) }

                val executor = buildExecutor(doerConfig, facade = facade)
                val result = executor.execute()

                result shouldBe PartResult.Completed
                // 2 sends = main task + compaction
                facade.sendPayloadCalls shouldHaveSize 2
            }
        }
    }

    // ── Compaction: remaining=36 does NOT trigger ────────────────────

    describe("GIVEN a doer-only executor with context just above threshold") {
        describe("WHEN remaining=36 is above threshold=35") {

            it("THEN NO compaction is triggered") {
                val publicMd = createPublicMdFile()
                val dir = publicMd.parent
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)
                val facade = FakeAgentFacade()
                facade.onSpawn { buildHandle("doer") }
                facade.onSendPayloadAndAwaitSignal { _, _ -> AgentSignal.Done(DoneResult.COMPLETED) }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 36) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                facade.sendPayloadCalls shouldHaveSize 1
            }
        }
    }

    // ── Compaction: Reviewer PASS + compaction failure → AgentCrashed ──

    describe("GIVEN a doer+reviewer executor where reviewer has low context") {
        describe("WHEN reviewer signals PASS but compaction fails (missing PRIVATE.md)") {

            it("THEN the result is AgentCrashed, not Completed") {
                val dir = Files.createTempDirectory("compaction-reviewer-pass-fail")
                val doerPublicMd = dir.resolve("doer-PUBLIC.md")
                Files.writeString(doerPublicMd, "doer output")
                val reviewerPublicMd = dir.resolve("reviewer-PUBLIC.md")
                Files.writeString(reviewerPublicMd, "reviewer output")
                val reviewerPrivateMd = dir.resolve("reviewer-PRIVATE.md")
                // Note: NOT creating reviewer-PRIVATE.md — compaction will fail validation

                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd, privateMdPath = reviewerPrivateMd)

                val doerHandle = buildHandle("doer")
                val reviewerHandle = buildHandle("reviewer", sessionId = "session-reviewer")

                // Signal sequence:
                // 1. Doer Done(COMPLETED) — healthy context, no compaction
                // 2. Reviewer PASS — low context triggers compaction
                // 3. Reviewer SelfCompacted — but PRIVATE.md is missing
                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),  // doer
                    AgentSignal.Done(DoneResult.PASS),       // reviewer
                    AgentSignal.SelfCompacted,               // reviewer compaction (PRIVATE.md missing)
                ))

                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(doerHandle, reviewerHandle))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }

                // Doer: healthy context (80). Reviewer: low context (20) — triggers compaction
                var contextReadCount = 0
                facade.onReadContextWindowState {
                    contextReadCount++
                    if (contextReadCount == 1) ContextWindowState(remainingPercentage = 80)
                    else ContextWindowState(remainingPercentage = 20)
                }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()

                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
                (result as PartResult.AgentCrashed).details shouldContain "PRIVATE.md"
            }
        }
    }

    // ── Compaction: Crashed signal during compaction kills session ──────

    describe("GIVEN a doer-only executor with low context") {
        describe("WHEN agent crashes during compaction") {

            it("THEN killSession is called for the crashed session") {
                val dir = Files.createTempDirectory("compaction-crash-kill")
                val publicMd = dir.resolve("PUBLIC.md")
                Files.writeString(publicMd, "# Output")
                val privateMd = dir.resolve("PRIVATE.md")
                val doerConfig = buildDoerConfig(publicMd, privateMdPath = privateMd)

                val doerHandle = buildHandle("doer")
                val signalQueue = ArrayDeque(listOf(
                    AgentSignal.Done(DoneResult.COMPLETED),
                    AgentSignal.Crashed("Agent timed out"),
                ))

                val facade = FakeAgentFacade()
                facade.onSpawn { doerHandle }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 20) }

                val executor = buildExecutor(doerConfig, facade = facade)
                executor.execute()

                // killSession should be called: once inside performCompaction (Crashed branch),
                // and the caller does NOT call killAllSessions for CompactionFailed.
                // So we expect exactly 1 kill call from the Crashed branch.
                facade.killSessionCalls shouldHaveSize 1
                facade.killSessionCalls[0] shouldBe doerHandle
            }
        }
    }

    // ── Doer+Reviewer: Part Completion Guard — PASS with empty pending → Completed ──

    describe("GIVEN a doer+reviewer executor with empty feedback pending directory") {
        describe("WHEN reviewer signals PASS") {

            it("THEN the result is PartResult.Completed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val feedbackDir = Files.createTempDirectory("feedback-guard-test")
                Files.createDirectories(feedbackDir.resolve("pending"))
                Files.createDirectories(feedbackDir.resolve("addressed"))

                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd, feedbackDir = feedbackDir)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )
                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }
        }
    }

    // ── Doer+Reviewer: Part Completion Guard — PASS with critical in pending → AgentCrashed ──

    describe("GIVEN a doer+reviewer executor with critical feedback file in pending") {
        describe("WHEN reviewer signals PASS") {

            it("THEN the result is PartResult.AgentCrashed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val feedbackDir = Files.createTempDirectory("feedback-guard-test")
                val pendingDir = feedbackDir.resolve("pending")
                Files.createDirectories(pendingDir)
                Files.createDirectories(feedbackDir.resolve("addressed"))
                Files.writeString(pendingDir.resolve("critical__missing-null-check.md"), "# Critical issue")

                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd, feedbackDir = feedbackDir)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )
                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Doer+Reviewer: Part Completion Guard — PASS with important in pending → AgentCrashed ──

    describe("GIVEN a doer+reviewer executor with important feedback file in pending") {
        describe("WHEN reviewer signals PASS") {

            it("THEN the result is PartResult.AgentCrashed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val feedbackDir = Files.createTempDirectory("feedback-guard-test")
                val pendingDir = feedbackDir.resolve("pending")
                Files.createDirectories(pendingDir)
                Files.createDirectories(feedbackDir.resolve("addressed"))
                Files.writeString(pendingDir.resolve("important__error-handling.md"), "# Important issue")

                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd, feedbackDir = feedbackDir)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )
                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result.shouldBeInstanceOf<PartResult.AgentCrashed>()
            }
        }
    }

    // ── Doer+Reviewer: Part Completion Guard — PASS with only optional → Completed + moved ──

    describe("GIVEN a doer+reviewer executor with only optional feedback files in pending") {
        describe("WHEN reviewer signals PASS") {

            it("THEN the result is PartResult.Completed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val feedbackDir = Files.createTempDirectory("feedback-guard-test")
                val pendingDir = feedbackDir.resolve("pending")
                val addressedDir = feedbackDir.resolve("addressed")
                Files.createDirectories(pendingDir)
                Files.createDirectories(addressedDir)
                Files.writeString(pendingDir.resolve("optional__naming.md"), "# Naming suggestion")

                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd, feedbackDir = feedbackDir)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )
                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                val result = executor.execute()
                result shouldBe PartResult.Completed
            }

            it("THEN optional files are moved from pending to addressed") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val feedbackDir = Files.createTempDirectory("feedback-guard-test")
                val pendingDir = feedbackDir.resolve("pending")
                val addressedDir = feedbackDir.resolve("addressed")
                Files.createDirectories(pendingDir)
                Files.createDirectories(addressedDir)
                Files.writeString(pendingDir.resolve("optional__naming.md"), "# Naming suggestion")

                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd, feedbackDir = feedbackDir)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.PASS),
                    )
                )
                val facade = FakeAgentFacade()
                val spawnQueue = ArrayDeque(listOf(buildHandle("doer"), buildHandle("reviewer", sessionId = "s2")))
                facade.onSpawn { spawnQueue.removeFirst() }
                facade.onSendPayloadAndAwaitSignal { _, _ -> signalQueue.removeFirst() }
                facade.onReadContextWindowState { ContextWindowState(remainingPercentage = 80) }

                val executor = buildExecutor(doerConfig, reviewerConfig = reviewerCfg, facade = facade)
                executor.execute()

                Files.exists(pendingDir.resolve("optional__naming.md")) shouldBe false
                Files.exists(addressedDir.resolve("optional__naming.md")) shouldBe true
            }
        }
    }
})
