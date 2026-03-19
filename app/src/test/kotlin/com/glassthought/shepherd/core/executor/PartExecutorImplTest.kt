package com.glassthought.shepherd.core.executor

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
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
import com.glassthought.shepherd.core.context.AgentInstructionRequest
import com.glassthought.shepherd.core.context.ContextForAgentProvider
import com.glassthought.shepherd.core.context.ExecutionContext
import com.glassthought.shepherd.core.data.AgentType
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
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class PartExecutorImplTest : AsgardDescribeSpec({

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

    fun buildDoerConfig(publicMdPath: Path): SubPartConfig = SubPartConfig(
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
        privateMdPath = null,
        executionContext = testExecutionContext,
    )

    fun buildReviewerConfig(publicMdPath: Path, feedbackDir: Path? = null): SubPartConfig = SubPartConfig(
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
        privateMdPath = null,
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
    ): PartExecutorImpl = PartExecutorImpl(
        doerConfig = doerConfig,
        reviewerConfig = reviewerConfig,
        deps = PartExecutorDeps(
            agentFacade = facade,
            contextForAgentProvider = contextProvider,
            gitCommitStrategy = gitCommitStrategy,
            failedToConvergeUseCase = failedToConvergeUseCase,
            outFactory = outFactory,
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
    // NOTE: With the inner feedback loop architecture, after NEEDS_ITERATION the inner loop
    // handles doer re-instruction per-item. When innerFeedbackLoop is null (these tests),
    // the inner loop is skipped and the reviewer is re-instructed directly.
    // Flow: doer COMPLETED -> reviewer NEEDS_ITERATION -> reviewer PASS (3 signals).

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
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION),
                        AgentSignal.Done(DoneResult.PASS),
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

                // 3 Done signals = 3 git commits (doer COMPLETED, reviewer NEEDS_ITERATION,
                // reviewer PASS). Inner loop doer re-instruction is handled by InnerFeedbackLoop.
                gitStrategy.calls shouldHaveSize 3
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
        describe("WHEN doer COMPLETED -> reviewer NEEDS_ITERATION -> reviewer PASS") {

            it("THEN readContextWindowState is called 3 times (once per Done signal)") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION),
                        AgentSignal.Done(DoneResult.PASS),
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

                // 3 Done signals = 3 readContextWindowState calls.
                // Inner loop doer re-instruction is handled by InnerFeedbackLoop (null here).
                facade.readContextWindowStateCalls shouldHaveSize 3
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
        describe("WHEN doer COMPLETED -> reviewer NEEDS_ITERATION -> reviewer PASS") {

            it("THEN sendPayloadAndAwaitSignal is called 3 times") {
                val doerPublicMd = createPublicMdFile("doer output")
                val reviewerPublicMd = createPublicMdFile("reviewer output")
                val doerConfig = buildDoerConfig(doerPublicMd)
                val reviewerCfg = buildReviewerConfig(reviewerPublicMd)

                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION),
                        AgentSignal.Done(DoneResult.PASS),
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

                // 3 signals: doer instructions, reviewer instructions, reviewer re-instructions.
                // Inner loop doer re-instruction is handled by InnerFeedbackLoop (null here).
                facade.sendPayloadCalls shouldHaveSize 3
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
                // (inner loop re-instructs doer via ReInstructAndAwait — not through facade),
                // reviewer PASS
                val signalQueue = ArrayDeque(
                    listOf(
                        AgentSignal.Done(DoneResult.COMPLETED),       // doer
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION), // reviewer
                        AgentSignal.Done(DoneResult.PASS),            // reviewer after inner loop
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
                        AgentSignal.Done(DoneResult.COMPLETED),
                        AgentSignal.Done(DoneResult.NEEDS_ITERATION),
                        AgentSignal.Done(DoneResult.PASS),
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
})
