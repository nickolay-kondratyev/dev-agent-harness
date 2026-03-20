package com.glassthought.shepherd.core.creator

import com.asgard.core.out.impl.NoOpOutFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentResult
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import com.glassthought.shepherd.core.executor.PartExecutor
import com.glassthought.shepherd.core.executor.PartExecutorFactory
import com.glassthought.shepherd.core.executor.PartExecutorFactoryCreator
import com.glassthought.shepherd.core.infra.ConsoleOutput
import com.glassthought.shepherd.core.infra.ProcessExiter
import com.glassthought.shepherd.core.initializer.data.ShepherdContext
import com.glassthought.shepherd.core.state.CurrentStateInitializer
import com.glassthought.shepherd.core.state.CurrentStateInitializerImpl
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.SubPart
import com.glassthought.shepherd.core.supporting.git.BranchNameBuilder
import com.glassthought.shepherd.core.supporting.git.GitBranchManager
import com.glassthought.shepherd.core.supporting.git.TryNResolver
import com.glassthought.shepherd.core.supporting.git.WorkingTreeValidator
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import com.glassthought.shepherd.core.supporting.ticket.TicketParser
import com.glassthought.shepherd.core.time.Clock
import com.glassthought.shepherd.core.time.TestClock
import com.glassthought.shepherd.core.workflow.WorkflowDefinition
import com.glassthought.shepherd.core.workflow.WorkflowParser
import com.glassthought.shepherd.usecase.finalcommit.FinalCommitUseCase
import com.glassthought.shepherd.core.creator.FinalCommitUseCaseFactory
import com.glassthought.shepherd.usecase.healthmonitoring.AllSessionsKiller
import com.glassthought.shepherd.usecase.planning.SetupPlanUseCase
import com.glassthought.shepherd.usecase.ticketstatus.TicketStatusUpdater
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

// ── Test Fakes ─────────────────────────────────────────────────────────

private class FakeWorkflowParser(
    private val result: WorkflowDefinition,
) : WorkflowParser {
    var parsedWorkflowName: String? = null
    override suspend fun parse(workflowName: String, workingDirectory: Path): WorkflowDefinition {
        parsedWorkflowName = workflowName
        return result
    }
}

private class FakeTicketParser(
    private val result: TicketData,
) : TicketParser {
    override suspend fun parse(path: Path): TicketData = result
}

private class FakeWorkingTreeValidator(
    private val shouldFail: Boolean = false,
) : WorkingTreeValidator {
    var validateCalled = false
    override suspend fun validate() {
        validateCalled = true
        if (shouldFail) {
            error("Working tree is dirty")
        }
    }
}

private class FakeTryNResolver(
    private val result: Int = 1,
) : TryNResolver {
    var resolvedTicketData: TicketData? = null
    override suspend fun resolve(ticketData: TicketData): Int {
        resolvedTicketData = ticketData
        return result
    }
}

private class FakeGitBranchManager(
    private val currentBranch: String = "main",
) : GitBranchManager {
    var createdBranchName: String? = null
    override suspend fun createAndCheckout(branchName: String) {
        createdBranchName = branchName
    }

    override suspend fun getCurrentBranch(): String = currentBranch
}

private class FakeConsoleOutput : ConsoleOutput {
    val redMessages = mutableListOf<String>()
    val greenMessages = mutableListOf<String>()
    override fun printlnRed(message: String) { redMessages.add(message) }
    override fun printlnGreen(message: String) { greenMessages.add(message) }
}

private class FakeProcessExitException(val exitCode: Int) : RuntimeException("FakeExit($exitCode)")

private class FakeProcessExiter : ProcessExiter {
    override fun exit(code: Int): Nothing = throw FakeProcessExitException(code)
}

private class FakeAllSessionsKiller : AllSessionsKiller {
    var killCalled = false
    override suspend fun killAllSessions() {
        killCalled = true
    }
}

// ── Helpers ────────────────────────────────────────────────────────────

private val VALID_TICKET_DATA = TicketData(
    id = "nid_abc123",
    title = "Implement feature X",
    status = "in_progress",
    description = "Some description",
)

private val STRAIGHTFORWARD_WORKFLOW = WorkflowDefinition(
    name = "straightforward",
    parts = listOf(
        Part(
            name = "main",
            phase = Phase.EXECUTION,
            description = "main part",
            subParts = listOf(
                SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet"),
            ),
        ),
    ),
)

private val WITH_PLANNING_WORKFLOW = WorkflowDefinition(
    name = "with-planning",
    planningParts = listOf(
        Part(
            name = "planning",
            phase = Phase.PLANNING,
            description = "planning part",
            subParts = listOf(
                SubPart(name = "planner", role = "PLANNER", agentType = "ClaudeCode", model = "opus"),
            ),
        ),
    ),
    executionPhasesFrom = "plan_flow.json",
)

private fun createTestShepherdContext(): ShepherdContext {
    val outFactory = NoOpOutFactory()
    val commandRunner = com.glassthought.shepherd.core.agent.tmux.util.TmuxCommandRunner()
    val communicator = com.glassthought.shepherd.core.agent.tmux.TmuxCommunicatorImpl(outFactory, commandRunner)
    val sessionManager = com.glassthought.shepherd.core.agent.tmux.TmuxSessionManager(
        outFactory, commandRunner, communicator,
    )
    val tmuxInfra = com.glassthought.shepherd.core.initializer.TmuxInfra(
        commandRunner = commandRunner,
        communicator = communicator,
        sessionManager = sessionManager,
    )
    val claudeCodeInfra = com.glassthought.shepherd.core.initializer.ClaudeCodeInfra(
        agentTypeAdapter = com.glassthought.shepherd.core.agent.adapter.ClaudeCodeAdapter.create(
            claudeProjectsDir = Path.of("/tmp/test-claude-projects"),
            outFactory = outFactory,
        ),
    )
    val infra = com.glassthought.shepherd.core.initializer.Infra(
        outFactory = outFactory,
        tmux = tmuxInfra,
        claudeCode = claudeCodeInfra,
    )
    val noOpRunner = NonInteractiveAgentRunner {
        NonInteractiveAgentResult.Success(output = "")
    }
    return ShepherdContext(infra = infra, nonInteractiveAgentRunner = noOpRunner)
}

@Suppress("LongMethod")
private fun createCreator(
    workflowParser: WorkflowParser = FakeWorkflowParser(STRAIGHTFORWARD_WORKFLOW),
    ticketParser: TicketParser = FakeTicketParser(VALID_TICKET_DATA),
    workingTreeValidator: WorkingTreeValidator = FakeWorkingTreeValidator(),
    tryNResolver: TryNResolver = FakeTryNResolver(1),
    gitBranchManager: GitBranchManager = FakeGitBranchManager(),
    currentStateInitializer: CurrentStateInitializer = CurrentStateInitializerImpl(),
    clock: Clock = TestClock(),
    consoleOutput: ConsoleOutput = FakeConsoleOutput(),
    processExiter: ProcessExiter = FakeProcessExiter(),
    repoRoot: Path = Files.createTempDirectory("creator-test"),
): TicketShepherdCreatorImpl {
    return TicketShepherdCreatorImpl(
        workflowParser = workflowParser,
        ticketParser = ticketParser,
        workingTreeValidator = workingTreeValidator,
        tryNResolver = tryNResolver,
        gitBranchManager = gitBranchManager,
        currentStateInitializer = currentStateInitializer,
        clock = clock,
        consoleOutput = consoleOutput,
        processExiter = processExiter,
        setupPlanUseCaseFactory = SetupPlanUseCaseFactory { _, _ -> SetupPlanUseCase { emptyList() } },
        partExecutorFactoryCreator = PartExecutorFactoryCreator { _ ->
            PartExecutorFactory { PartExecutor { PartResult.Completed } }
        },
        finalCommitUseCaseFactory = FinalCommitUseCaseFactory { _, _, _ ->
            FinalCommitUseCase { /* no-op for tests */ }
        },
        ticketStatusUpdaterFactory = TicketStatusUpdaterFactory { _, _ -> TicketStatusUpdater { /* no-op */ } },
        allSessionsKillerFactory = AllSessionsKillerFactory { _ -> FakeAllSessionsKiller() },
        repoRoot = repoRoot,
    )
}

// ── Tests ──────────────────────────────────────────────────────────────

@Suppress("LongMethod")
class TicketShepherdCreatorTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        val shepherdContext = createTestShepherdContext()

        // ── Ticket validation: missing id ──

        describe("GIVEN a ticket with blank id") {
            val ticketData = VALID_TICKET_DATA.copy(id = "")
            val creator = createCreator(ticketParser = FakeTicketParser(ticketData))

            describe("WHEN create() is called") {
                it("THEN fails with IllegalStateException mentioning 'id'") {
                    val exception = shouldThrow<IllegalStateException> {
                        creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    }
                    exception.message shouldContain "id"
                }
            }
        }

        // ── Ticket validation: blank title ──

        describe("GIVEN a ticket with blank title") {
            val ticketData = VALID_TICKET_DATA.copy(title = "")
            val creator = createCreator(ticketParser = FakeTicketParser(ticketData))

            describe("WHEN create() is called") {
                it("THEN fails with IllegalStateException mentioning 'title'") {
                    val exception = shouldThrow<IllegalStateException> {
                        creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    }
                    exception.message shouldContain "title"
                }
            }
        }

        // ── Ticket validation: missing status ──

        describe("GIVEN a ticket with null status") {
            val ticketData = VALID_TICKET_DATA.copy(status = null)
            val creator = createCreator(ticketParser = FakeTicketParser(ticketData))

            describe("WHEN create() is called") {
                it("THEN fails with IllegalStateException mentioning 'status'") {
                    val exception = shouldThrow<IllegalStateException> {
                        creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    }
                    exception.message shouldContain "status"
                }
            }
        }

        // ── Ticket validation: wrong status ──

        describe("GIVEN a ticket with status 'open' instead of 'in_progress'") {
            val ticketData = VALID_TICKET_DATA.copy(status = "open")
            val creator = createCreator(ticketParser = FakeTicketParser(ticketData))

            describe("WHEN create() is called") {
                it("THEN fails with IllegalStateException mentioning 'in_progress'") {
                    val exception = shouldThrow<IllegalStateException> {
                        creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    }
                    exception.message shouldContain "in_progress"
                }

                it("THEN error message mentions the actual status 'open'") {
                    val exception = shouldThrow<IllegalStateException> {
                        creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    }
                    exception.message shouldContain "open"
                }
            }
        }

        // ── Working tree validation: dirty tree ──

        describe("GIVEN a dirty working tree") {
            val creator = createCreator(
                workingTreeValidator = FakeWorkingTreeValidator(shouldFail = true),
            )

            describe("WHEN create() is called") {
                it("THEN fails with IllegalStateException") {
                    shouldThrow<IllegalStateException> {
                        creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    }
                }
            }
        }

        // ── Clean working tree proceeds ──

        describe("GIVEN a clean working tree and valid ticket") {
            val validator = FakeWorkingTreeValidator(shouldFail = false)
            val creator = createCreator(workingTreeValidator = validator)

            describe("WHEN create() is called") {
                it("THEN working tree validation is called") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    validator.validateCalled shouldBe true
                }
            }
        }

        // ── Originating branch is recorded ──

        describe("GIVEN the current branch is 'feature/existing'") {
            val gitManager = FakeGitBranchManager(currentBranch = "feature/existing")
            val creator = createCreator(gitBranchManager = gitManager)

            describe("WHEN create() is called") {
                it("THEN originatingBranch is 'feature/existing'") {
                    val result = creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    result.originatingBranch shouldBe "feature/existing"
                }
            }
        }

        // ── Try-N resolution ──

        describe("GIVEN try-N resolver returns 3 (first two try dirs exist)") {
            val resolver = FakeTryNResolver(result = 3)
            val creator = createCreator(tryNResolver = resolver)

            describe("WHEN create() is called") {
                it("THEN tryNumber is 3") {
                    val result = creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    result.tryNumber shouldBe 3
                }

                it("THEN resolver was called with the ticket data") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    resolver.resolvedTicketData shouldBe VALID_TICKET_DATA
                }
            }
        }

        // ── Feature branch created with correct name ──

        describe("GIVEN valid ticket and try number 2") {
            val gitManager = FakeGitBranchManager()
            val creator = createCreator(
                tryNResolver = FakeTryNResolver(result = 2),
                gitBranchManager = gitManager,
            )

            describe("WHEN create() is called") {
                it("THEN feature branch is created with correct name format") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    val expected = BranchNameBuilder.build(VALID_TICKET_DATA, 2)
                    gitManager.createdBranchName shouldBe expected
                }
            }
        }

        // ── Straightforward workflow → TicketShepherd returned ──

        describe("GIVEN a straightforward workflow") {
            val creator = createCreator(
                workflowParser = FakeWorkflowParser(STRAIGHTFORWARD_WORKFLOW),
            )

            describe("WHEN create() is called") {
                it("THEN returns TicketShepherd with tryNumber set") {
                    val result = creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    result.tryNumber shouldBe 1
                }
            }
        }

        // ── With-planning workflow → TicketShepherd returned ──

        describe("GIVEN a with-planning workflow") {
            val tempDir = Files.createTempDirectory("planning-test")
            val creator = createCreator(
                workflowParser = FakeWorkflowParser(WITH_PLANNING_WORKFLOW),
                repoRoot = tempDir,
            )

            describe("WHEN create() is called") {
                it("THEN returns TicketShepherd with tryNumber set") {
                    val result = creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "with-planning")
                    result.tryNumber shouldBe 1
                }

                it("THEN CurrentState on disk contains planning phase parts") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "with-planning")
                    val branchName = BranchNameBuilder.build(VALID_TICKET_DATA, 1)
                    val aiOutputStructure = com.glassthought.shepherd.core.filestructure.AiOutputStructure(
                        tempDir, branchName,
                    )
                    val json = Files.readString(aiOutputStructure.currentStateJson())
                    json shouldContain "\"phase\" : \"planning\""
                }
            }
        }

        // ── .ai_out/ directory structure is created ──

        describe("GIVEN a straightforward workflow with valid ticket") {
            val tempDir = Files.createTempDirectory("ai-out-test")
            val creator = createCreator(repoRoot = tempDir)

            describe("WHEN create() is called") {
                it("THEN creates the harness_private directory") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    val branchName = BranchNameBuilder.build(VALID_TICKET_DATA, 1)
                    val aiOutputStructure = com.glassthought.shepherd.core.filestructure.AiOutputStructure(
                        tempDir, branchName,
                    )
                    Files.isDirectory(aiOutputStructure.harnessPrivateDir()) shouldBe true
                }

                it("THEN creates the shared/plan directory") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    val branchName = BranchNameBuilder.build(VALID_TICKET_DATA, 1)
                    val aiOutputStructure = com.glassthought.shepherd.core.filestructure.AiOutputStructure(
                        tempDir, branchName,
                    )
                    Files.isDirectory(aiOutputStructure.sharedPlanDir()) shouldBe true
                }

                it("THEN creates the execution sub-part comm directories") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    val branchName = BranchNameBuilder.build(VALID_TICKET_DATA, 1)
                    val aiOutputStructure = com.glassthought.shepherd.core.filestructure.AiOutputStructure(
                        tempDir, branchName,
                    )
                    Files.isDirectory(aiOutputStructure.executionCommInDir("main", "impl")) shouldBe true
                    Files.isDirectory(aiOutputStructure.executionCommOutDir("main", "impl")) shouldBe true
                }
            }
        }

        // ── CurrentState is flushed to disk ──

        describe("GIVEN a valid creator setup") {
            val tempDir = Files.createTempDirectory("flush-test")
            val creator = createCreator(repoRoot = tempDir)

            describe("WHEN create() is called") {
                it("THEN current_state.json exists on disk") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "straightforward")
                    val branchName = BranchNameBuilder.build(VALID_TICKET_DATA, 1)
                    val aiOutputStructure = com.glassthought.shepherd.core.filestructure.AiOutputStructure(
                        tempDir, branchName,
                    )
                    Files.exists(aiOutputStructure.currentStateJson()) shouldBe true
                }
            }
        }

        // ── Workflow name is forwarded to parser ──

        describe("GIVEN a workflow parser") {
            val parser = FakeWorkflowParser(STRAIGHTFORWARD_WORKFLOW)
            val creator = createCreator(workflowParser = parser)

            describe("WHEN create() is called with workflow name 'my-workflow'") {
                it("THEN workflow parser receives the correct workflow name") {
                    creator.create(shepherdContext, Path.of("/tmp/ticket.md"), "my-workflow")
                    parser.parsedWorkflowName shouldBe "my-workflow"
                }
            }
        }
    },
)
