package com.glassthought.shepherd.usecase.planning

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.executor.PartExecutor
import com.glassthought.shepherd.core.state.CurrentState
import com.glassthought.shepherd.core.state.Part
import com.glassthought.shepherd.core.state.PartResult
import com.glassthought.shepherd.core.state.Phase
import com.glassthought.shepherd.core.state.PlanConversionException
import com.glassthought.shepherd.core.state.PlanFlowConverter
import com.glassthought.shepherd.usecase.healthmonitoring.FailedToExecutePlanUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Thrown by [FakeFailedToExecutePlanUseCase] to simulate process exit.
 * Allows tests to verify that [FailedToExecutePlanUseCase.handleFailure] was called
 * without actually exiting the process.
 */
class TestFailureException(val failedResult: PartResult) :
    RuntimeException("Test failure exit: $failedResult")

class DetailedPlanningUseCaseImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    // ── Fakes ──────────────────────────────────────────────────────────────

    /**
     * Fake [FailedToExecutePlanUseCase] that throws [TestFailureException]
     * instead of exiting the process. Records calls for verification.
     */
    class FakeFailedToExecutePlanUseCase : FailedToExecutePlanUseCase {
        val calls = mutableListOf<PartResult>()

        override suspend fun handleFailure(failedResult: PartResult): Nothing {
            calls.add(failedResult)
            throw TestFailureException(failedResult)
        }
    }

    /**
     * Fake [PlanFlowConverter] with programmable behavior.
     * Each call to [convertAndAppend] invokes the next handler in the queue.
     */
    class FakePlanFlowConverter : PlanFlowConverter {
        private val handlers = ArrayDeque<suspend (CurrentState) -> List<Part>>()
        val calls = mutableListOf<CurrentState>()

        fun onConvertAndAppend(handler: suspend (CurrentState) -> List<Part>) {
            handlers.addLast(handler)
        }

        override suspend fun convertAndAppend(currentState: CurrentState): List<Part> {
            calls.add(currentState)
            val handler = handlers.removeFirstOrNull()
                ?: error("FakePlanFlowConverter: convertAndAppend not programmed for call #${calls.size}")
            return handler(currentState)
        }
    }

    /**
     * Fake [PartExecutor] that returns a pre-configured [PartResult].
     */
    class FakePartExecutor(private val result: PartResult) : PartExecutor {
        var executeCalled = false
        override suspend fun execute(): PartResult {
            executeCalled = true
            return result
        }
    }

    /**
     * Fake [PlanningPartExecutorFactory] that returns executors from a queue.
     */
    class FakePlanningPartExecutorFactory : PlanningPartExecutorFactory {
        private val executors = ArrayDeque<PartExecutor>()
        val createCalls = mutableListOf<Unit>()

        fun onCreate(executor: PartExecutor) {
            executors.addLast(executor)
        }

        override fun create(): PartExecutor {
            createCalls.add(Unit)
            return executors.removeFirstOrNull()
                ?: error("FakePlanningPartExecutorFactory: no executor programmed for call #${createCalls.size}")
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    val sampleExecutionParts = listOf(
        Part(
            name = "implementation",
            phase = Phase.EXECUTION,
            description = "Implement the feature",
            subParts = emptyList(),
        ),
        Part(
            name = "testing",
            phase = Phase.EXECUTION,
            description = "Write tests",
            subParts = emptyList(),
        ),
    )

    fun buildCurrentState(): CurrentState = CurrentState(parts = mutableListOf())

    fun buildUseCase(
        factory: FakePlanningPartExecutorFactory,
        converter: FakePlanFlowConverter,
        failedUseCase: FakeFailedToExecutePlanUseCase,
        currentState: CurrentState = buildCurrentState(),
        maxConversionRetries: Int = 3,
    ): DetailedPlanningUseCaseImpl = DetailedPlanningUseCaseImpl(
        partExecutorFactory = factory,
        planFlowConverter = converter,
        failedToExecutePlanUseCase = failedUseCase,
        currentState = currentState,
        maxConversionRetries = maxConversionRetries,
        outFactory = outFactory,
    )

    // ── Happy path ─────────────────────────────────────────────────────────

    describe("GIVEN planning executor completes successfully") {
        describe("AND plan conversion succeeds") {
            it("THEN execute returns the execution parts") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.Completed))
                val converter = FakePlanFlowConverter()
                converter.onConvertAndAppend { sampleExecutionParts }
                val useCase = buildUseCase(factory, converter, FakeFailedToExecutePlanUseCase())

                val result = useCase.execute()
                result shouldBe sampleExecutionParts
            }

            it("THEN the returned list has correct size") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.Completed))
                val converter = FakePlanFlowConverter()
                converter.onConvertAndAppend { sampleExecutionParts }
                val useCase = buildUseCase(factory, converter, FakeFailedToExecutePlanUseCase())

                val result = useCase.execute()
                result shouldHaveSize 2
            }

            it("THEN failedToExecutePlanUseCase is NOT called") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.Completed))
                val converter = FakePlanFlowConverter()
                converter.onConvertAndAppend { sampleExecutionParts }
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val useCase = buildUseCase(factory, converter, failedUseCase)

                useCase.execute()
                failedUseCase.calls shouldHaveSize 0
            }
        }
    }

    // ── Planning failure: FailedWorkflow ────────────────────────────────────

    describe("GIVEN planning executor returns FailedWorkflow") {
        describe("WHEN execute is called") {
            it("THEN failedToExecutePlanUseCase.handleFailure is called") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.FailedWorkflow("missing dependency")))

                val converter = FakePlanFlowConverter()
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val useCase = buildUseCase(factory, converter, failedUseCase)

                shouldThrow<TestFailureException> {
                    useCase.execute()
                }
                failedUseCase.calls shouldHaveSize 1
            }

            it("THEN the PartResult passed to handleFailure is FailedWorkflow with correct reason") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.FailedWorkflow("missing dependency")))

                val converter = FakePlanFlowConverter()
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val useCase = buildUseCase(factory, converter, failedUseCase)

                val thrown = shouldThrow<TestFailureException> { useCase.execute() }
                thrown.failedResult shouldBe PartResult.FailedWorkflow("missing dependency")
            }

            it("THEN planFlowConverter.convertAndAppend is NOT called") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.FailedWorkflow("blocked")))

                val converter = FakePlanFlowConverter()
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val useCase = buildUseCase(factory, converter, failedUseCase)

                shouldThrow<TestFailureException> { useCase.execute() }
                converter.calls shouldHaveSize 0
            }
        }
    }

    // ── Planning failure: AgentCrashed ──────────────────────────────────────

    describe("GIVEN planning executor returns AgentCrashed") {
        describe("WHEN execute is called") {
            it("THEN failedToExecutePlanUseCase.handleFailure is called with AgentCrashed") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.AgentCrashed("agent unresponsive")))

                val converter = FakePlanFlowConverter()
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val useCase = buildUseCase(factory, converter, failedUseCase)

                val thrown = shouldThrow<TestFailureException> { useCase.execute() }
                thrown.failedResult shouldBe PartResult.AgentCrashed("agent unresponsive")
            }
        }
    }

    // ── Planning failure: FailedToConverge ──────────────────────────────────

    describe("GIVEN planning executor returns FailedToConverge") {
        describe("WHEN execute is called") {
            it("THEN failedToExecutePlanUseCase.handleFailure is called with FailedToConverge") {
                val factory = FakePlanningPartExecutorFactory()
                factory.onCreate(FakePartExecutor(PartResult.FailedToConverge("budget exhausted")))

                val converter = FakePlanFlowConverter()
                val failedUseCase = FakeFailedToExecutePlanUseCase()
                val useCase = buildUseCase(factory, converter, failedUseCase)

                val thrown = shouldThrow<TestFailureException> { useCase.execute() }
                thrown.failedResult shouldBe PartResult.FailedToConverge("budget exhausted")
            }
        }
    }

    // ── PlanConversionException: retries then succeeds ──────────────────────

    describe("GIVEN planning executor completes") {
        describe("AND first plan conversion throws PlanConversionException") {
            describe("AND second plan conversion succeeds") {
                it("THEN execute returns execution parts from second attempt").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val factory = FakePlanningPartExecutorFactory()
                    factory.onCreate(FakePartExecutor(PartResult.Completed))
                    factory.onCreate(FakePartExecutor(PartResult.Completed))

                    val converter = FakePlanFlowConverter()
                    converter.onConvertAndAppend { throw PlanConversionException("invalid schema") }
                    converter.onConvertAndAppend { sampleExecutionParts }

                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val useCase = buildUseCase(factory, converter, failedUseCase, maxConversionRetries = 3)

                    val result = useCase.execute()
                    result shouldBe sampleExecutionParts
                }

                it("THEN the factory creates two executors (one per planning attempt)").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val factory = FakePlanningPartExecutorFactory()
                    factory.onCreate(FakePartExecutor(PartResult.Completed))
                    factory.onCreate(FakePartExecutor(PartResult.Completed))

                    val converter = FakePlanFlowConverter()
                    converter.onConvertAndAppend { throw PlanConversionException("invalid schema") }
                    converter.onConvertAndAppend { sampleExecutionParts }

                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val useCase = buildUseCase(factory, converter, failedUseCase, maxConversionRetries = 3)

                    useCase.execute()
                    factory.createCalls shouldHaveSize 2
                }

                it("THEN failedToExecutePlanUseCase is NOT called on successful retry").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val factory = FakePlanningPartExecutorFactory()
                    factory.onCreate(FakePartExecutor(PartResult.Completed))
                    factory.onCreate(FakePartExecutor(PartResult.Completed))

                    val converter = FakePlanFlowConverter()
                    converter.onConvertAndAppend { throw PlanConversionException("invalid schema") }
                    converter.onConvertAndAppend { sampleExecutionParts }

                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val useCase = buildUseCase(factory, converter, failedUseCase, maxConversionRetries = 3)

                    useCase.execute()
                    failedUseCase.calls shouldHaveSize 0
                }
            }
        }
    }

    // ── PlanConversionException: exhausts budget ────────────────────────────

    describe("GIVEN planning executor always completes") {
        describe("AND plan conversion always throws PlanConversionException") {
            describe("AND maxConversionRetries is 2") {
                it("THEN failedToExecutePlanUseCase.handleFailure is called").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val factory = FakePlanningPartExecutorFactory()
                    factory.onCreate(FakePartExecutor(PartResult.Completed))
                    factory.onCreate(FakePartExecutor(PartResult.Completed))

                    val converter = FakePlanFlowConverter()
                    converter.onConvertAndAppend { throw PlanConversionException("bad schema attempt 1") }
                    converter.onConvertAndAppend { throw PlanConversionException("bad schema attempt 2") }

                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val useCase = buildUseCase(factory, converter, failedUseCase, maxConversionRetries = 2)

                    shouldThrow<TestFailureException> { useCase.execute() }
                    failedUseCase.calls shouldHaveSize 1
                }

                it("THEN the failure result is FailedToConverge with conversion error message").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val factory = FakePlanningPartExecutorFactory()
                    factory.onCreate(FakePartExecutor(PartResult.Completed))
                    factory.onCreate(FakePartExecutor(PartResult.Completed))

                    val converter = FakePlanFlowConverter()
                    converter.onConvertAndAppend { throw PlanConversionException("bad schema attempt 1") }
                    converter.onConvertAndAppend { throw PlanConversionException("bad schema attempt 2") }

                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val useCase = buildUseCase(factory, converter, failedUseCase, maxConversionRetries = 2)

                    val thrown = shouldThrow<TestFailureException> { useCase.execute() }
                    val failedResult = thrown.failedResult
                    failedResult.shouldBeInstanceOf<PartResult.FailedToConverge>()
                    (failedResult as PartResult.FailedToConverge).summary shouldContain "Plan conversion failed"
                }

                it("THEN the factory created 2 executors (one per retry attempt)").config(
                    extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
                ) {
                    val factory = FakePlanningPartExecutorFactory()
                    factory.onCreate(FakePartExecutor(PartResult.Completed))
                    factory.onCreate(FakePartExecutor(PartResult.Completed))

                    val converter = FakePlanFlowConverter()
                    converter.onConvertAndAppend { throw PlanConversionException("bad schema attempt 1") }
                    converter.onConvertAndAppend { throw PlanConversionException("bad schema attempt 2") }

                    val failedUseCase = FakeFailedToExecutePlanUseCase()
                    val useCase = buildUseCase(factory, converter, failedUseCase, maxConversionRetries = 2)

                    shouldThrow<TestFailureException> { useCase.execute() }
                    factory.createCalls shouldHaveSize 2
                }
            }
        }
    }
})
