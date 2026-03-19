package com.glassthought.shepherd.core.supporting.git

import com.asgard.core.out.OutFactory
import com.asgard.core.processRunner.ProcessRunner
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// ── Test Fakes ──────────────────────────────────────────────────────────────

/**
 * Thrown by [FakeFailedToExecutePlanUseCase] to capture the escalation without exiting the process.
 */
internal class FakeFailureEscalationException(val partResult: PartResult) :
    RuntimeException("FakeEscalation")

internal class FakeFailedToExecutePlanUseCase : FailedToExecutePlanUseCase {
    var capturedResult: PartResult? = null
        private set

    override suspend fun handleFailure(failedResult: PartResult): Nothing {
        capturedResult = failedResult
        throw FakeFailureEscalationException(failedResult)
    }
}

internal class FakeGitIndexLockFileOperations(
    private var lockExists: Boolean = false,
) : GitIndexLockFileOperations {

    var deleteIndexLockCalled = false
        private set

    override suspend fun indexLockExists(): Boolean = lockExists

    override suspend fun deleteIndexLock(): Boolean {
        deleteIndexLockCalled = true
        lockExists = false
        return true
    }
}

/**
 * A [ProcessRunner] fake that allows configuring per-command responses.
 *
 * Commands not configured will throw [RuntimeException] (simulating git failure).
 */
internal class FakeProcessRunner : ProcessRunner {
    private val responses = mutableMapOf<String, Result<String>>()

    fun onCommand(vararg args: String, result: Result<String>) {
        responses[args.toList().joinToString(" ")] = result
    }

    override suspend fun runProcess(vararg input: String?): String {
        val key = input.filterNotNull().joinToString(" ")
        val result = responses[key]
            ?: error("Fake: unrecognized command [$key]")
        return result.getOrThrow()
    }

    override suspend fun runScript(script: com.asgard.core.file.File): String {
        error("Not implemented in fake")
    }

    override suspend fun runProcessV2(
        timeout: kotlin.time.Duration,
        vararg input: String?,
    ): com.asgard.core.processRunner.ProcessResult {
        error("Not implemented in fake")
    }
}

// ── Test Helpers ─────────────────────────────────────────────────────────────

private data class UseCaseTestFixture(
    val useCase: GitOperationFailureUseCase,
    val fakeFailedToExecutePlan: FakeFailedToExecutePlanUseCase,
    val fakeLockFileOps: FakeGitIndexLockFileOperations,
)

private val DEFAULT_CONTEXT = GitFailureContext(
    partName = "build",
    subPartName = "compile",
    iterationNumber = 1,
)

private val GIT_ADD_COMMAND = listOf("git", "add", "-A")

private const val INDEX_LOCK_ERROR = "fatal: Unable to create '/repo/.git/index.lock': File exists."
private const val UNABLE_TO_LOCK_ERROR = "error: unable to lock index file"
private const val UNRELATED_ERROR = "fatal: pathspec 'nonexistent' did not match any files"

private fun createUseCase(
    outFactory: OutFactory,
    processRunner: ProcessRunner = FakeProcessRunner(),
    failedUseCase: FakeFailedToExecutePlanUseCase = FakeFailedToExecutePlanUseCase(),
    lockOps: FakeGitIndexLockFileOperations = FakeGitIndexLockFileOperations(),
): UseCaseTestFixture {
    val useCase = GitOperationFailureUseCaseImpl(
        outFactory = outFactory,
        processRunner = processRunner,
        failedToExecutePlanUseCase = failedUseCase,
        indexLockFileOperations = lockOps,
    )
    return UseCaseTestFixture(useCase, failedUseCase, lockOps)
}

// ── Tests ───────────────────────────────────────────────────────────────────

class GitOperationFailureUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

        describe("GIVEN index.lock error AND lock file exists on disk") {
            describe("AND retry succeeds") {
                val fakeRunner = FakeProcessRunner().apply {
                    onCommand("git", "add", "-A", result = Result.success(""))
                    onCommand("git", "status", result = Result.success("nothing to commit"))
                }
                val lockOps = FakeGitIndexLockFileOperations(lockExists = true)
                val failedUseCase = FakeFailedToExecutePlanUseCase()

                it("THEN returns normally (no escalation)") {
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = failedUseCase,
                        lockOps = lockOps,
                    )

                    // Should not throw — recovery succeeded
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = INDEX_LOCK_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                it("THEN deletes the index.lock file") {
                    val localLockOps = FakeGitIndexLockFileOperations(lockExists = true)
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = FakeFailedToExecutePlanUseCase(),
                        lockOps = localLockOps,
                    )

                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = INDEX_LOCK_ERROR,
                        context = DEFAULT_CONTEXT,
                    )

                    localLockOps.deleteIndexLockCalled shouldBe true
                }

                it("THEN does NOT call FailedToExecutePlanUseCase") {
                    val localFailedUseCase = FakeFailedToExecutePlanUseCase()
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = localFailedUseCase,
                        lockOps = FakeGitIndexLockFileOperations(lockExists = true),
                    )

                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = INDEX_LOCK_ERROR,
                        context = DEFAULT_CONTEXT,
                    )

                    localFailedUseCase.capturedResult shouldBe null
                }
            }

            describe("AND retry fails") {
                val fakeRunner = FakeProcessRunner().apply {
                    // Retry will fail (no success configured for git add)
                    onCommand("git", "status", result = Result.success("On branch main"))
                }
                val lockOps = FakeGitIndexLockFileOperations(lockExists = true)

                it("THEN escalates to FailedToExecutePlanUseCase") {
                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = failedUseCase,
                        lockOps = lockOps,
                    )

                    shouldThrow<FakeFailureEscalationException> {
                        useCase.handleGitFailure(
                            gitCommand = GIT_ADD_COMMAND,
                            errorOutput = INDEX_LOCK_ERROR,
                            context = DEFAULT_CONTEXT,
                        )
                    }
                }

                it("THEN escalation contains FailedWorkflow with git command info") {
                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = failedUseCase,
                        lockOps = FakeGitIndexLockFileOperations(lockExists = true),
                    )

                    shouldThrow<FakeFailureEscalationException> {
                        useCase.handleGitFailure(
                            gitCommand = GIT_ADD_COMMAND,
                            errorOutput = INDEX_LOCK_ERROR,
                            context = DEFAULT_CONTEXT,
                        )
                    }

                    val result = failedUseCase.capturedResult as PartResult.FailedWorkflow
                    result.reason shouldContain "git add -A"
                }

                it("THEN deletes the index.lock file before retrying") {
                    val localLockOps = FakeGitIndexLockFileOperations(lockExists = true)
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = FakeFailedToExecutePlanUseCase(),
                        lockOps = localLockOps,
                    )

                    shouldThrow<FakeFailureEscalationException> {
                        useCase.handleGitFailure(
                            gitCommand = GIT_ADD_COMMAND,
                            errorOutput = INDEX_LOCK_ERROR,
                            context = DEFAULT_CONTEXT,
                        )
                    }

                    localLockOps.deleteIndexLockCalled shouldBe true
                }
            }
        }

        describe("GIVEN index.lock error but lock file does NOT exist on disk") {
            it("THEN escalates to FailedToExecutePlanUseCase immediately") {
                val lockOps = FakeGitIndexLockFileOperations(lockExists = false)
                val fakeRunner = FakeProcessRunner().apply {
                    onCommand("git", "status", result = Result.success("On branch main"))
                }
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = lockOps,
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = INDEX_LOCK_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }
            }

            it("THEN does NOT attempt to delete the lock file") {
                val lockOps = FakeGitIndexLockFileOperations(lockExists = false)
                val fakeRunner = FakeProcessRunner().apply {
                    onCommand("git", "status", result = Result.success("On branch main"))
                }
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = FakeFailedToExecutePlanUseCase(),
                    lockOps = lockOps,
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = INDEX_LOCK_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                lockOps.deleteIndexLockCalled shouldBe false
            }
        }

        describe("GIVEN non-lock-related error") {
            it("THEN escalates to FailedToExecutePlanUseCase immediately") {
                val lockOps = FakeGitIndexLockFileOperations(lockExists = false)
                val fakeRunner = FakeProcessRunner().apply {
                    onCommand("git", "status", result = Result.success("On branch main"))
                }
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = lockOps,
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }
            }

            it("THEN does NOT attempt to delete the lock file") {
                val lockOps = FakeGitIndexLockFileOperations(lockExists = true)
                val fakeRunner = FakeProcessRunner().apply {
                    onCommand("git", "status", result = Result.success("On branch main"))
                }
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = FakeFailedToExecutePlanUseCase(),
                    lockOps = lockOps,
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                lockOps.deleteIndexLockCalled shouldBe false
            }
        }

        describe("GIVEN 'unable to lock' error variant AND lock file exists") {
            describe("AND retry succeeds") {
                it("THEN returns normally (triggers fast-path)") {
                    val fakeRunner = FakeProcessRunner().apply {
                        onCommand("git", "add", "-A", result = Result.success(""))
                        onCommand("git", "status", result = Result.success("nothing to commit"))
                    }
                    val lockOps = FakeGitIndexLockFileOperations(lockExists = true)
                    val (useCase, _, _) = createUseCase(
                        outFactory = outFactory,
                        processRunner = fakeRunner,
                        failedUseCase = FakeFailedToExecutePlanUseCase(),
                        lockOps = lockOps,
                    )

                    // Should not throw
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNABLE_TO_LOCK_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }
            }
        }

        describe("GIVEN fail-fast escalation") {
            val fakeRunner = FakeProcessRunner().apply {
                onCommand("git", "status", result = Result.success("On branch main\nnothing to commit"))
            }

            it("THEN FailedWorkflow reason contains the git command") {
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = FakeGitIndexLockFileOperations(lockExists = false),
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                val reason = (failedUseCase.capturedResult as PartResult.FailedWorkflow).reason
                reason shouldContain "git add -A"
            }

            it("THEN FailedWorkflow reason contains the stderr") {
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = FakeGitIndexLockFileOperations(lockExists = false),
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                val reason = (failedUseCase.capturedResult as PartResult.FailedWorkflow).reason
                reason shouldContain UNRELATED_ERROR
            }

            it("THEN FailedWorkflow reason contains the part name") {
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = FakeGitIndexLockFileOperations(lockExists = false),
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                val reason = (failedUseCase.capturedResult as PartResult.FailedWorkflow).reason
                reason shouldContain "build"
            }

            it("THEN FailedWorkflow reason contains git status output") {
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = FakeGitIndexLockFileOperations(lockExists = false),
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = DEFAULT_CONTEXT,
                    )
                }

                val reason = (failedUseCase.capturedResult as PartResult.FailedWorkflow).reason
                reason shouldContain "On branch main"
            }

            it("THEN FailedWorkflow reason contains sub-part name") {
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = FakeGitIndexLockFileOperations(lockExists = false),
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = GitFailureContext(
                            partName = "deploy",
                            subPartName = "stage-files",
                            iterationNumber = 3,
                        ),
                    )
                }

                val reason = (failedUseCase.capturedResult as PartResult.FailedWorkflow).reason
                reason shouldContain "stage-files"
            }

            it("THEN FailedWorkflow reason contains iteration number") {
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val (useCase, _, _) = createUseCase(
                    outFactory = outFactory,
                    processRunner = fakeRunner,
                    failedUseCase = failedUseCase,
                    lockOps = FakeGitIndexLockFileOperations(lockExists = false),
                )

                shouldThrow<FakeFailureEscalationException> {
                    useCase.handleGitFailure(
                        gitCommand = GIT_ADD_COMMAND,
                        errorOutput = UNRELATED_ERROR,
                        context = GitFailureContext(
                            partName = "deploy",
                            subPartName = "stage-files",
                            iterationNumber = 3,
                        ),
                    )
                }

                val reason = (failedUseCase.capturedResult as PartResult.FailedWorkflow).reason
                reason shouldContain "3"
            }
        }
    },
)
