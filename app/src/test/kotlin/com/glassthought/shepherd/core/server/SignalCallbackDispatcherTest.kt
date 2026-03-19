package com.glassthought.shepherd.core.server

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.session.createTestSessionEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SignalCallbackDispatcherTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    val fixedInstant = Instant.parse("2026-03-19T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    // -------------------------------------------------------------------------
    // self-compacted
    // -------------------------------------------------------------------------

    describe("GIVEN a registered session") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry()
        sessionsState.register(guid, entry)
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(self-compacted, {handshakeGuid})") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.SELF_COMPACTED,
                payload = mapOf("handshakeGuid" to guid.value),
            )

            it("THEN returns Success with AgentSignal.SelfCompacted") {
                result.shouldBeInstanceOf<DispatchResult.Success>()
                result.signal shouldBe AgentSignal.SelfCompacted
            }

            it("THEN completes the signalDeferred with SelfCompacted") {
                entry.signalDeferred.isCompleted shouldBe true
                entry.signalDeferred.getCompleted() shouldBe AgentSignal.SelfCompacted
            }

            it("THEN updates lastActivityTimestamp") {
                entry.lastActivityTimestamp shouldBe fixedInstant
            }
        }
    }

    // -------------------------------------------------------------------------
    // done — completed
    // -------------------------------------------------------------------------

    describe("GIVEN a registered session AND done signal with completed result") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry()
        sessionsState.register(guid, entry)
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(done, {handshakeGuid, result=completed})") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf(
                    "handshakeGuid" to guid.value,
                    "result" to ProtocolVocabulary.DoneResult.COMPLETED,
                ),
            )

            it("THEN returns Success with AgentSignal.Done(COMPLETED)") {
                result.shouldBeInstanceOf<DispatchResult.Success>()
                result.signal shouldBe AgentSignal.Done(DoneResult.COMPLETED)
            }

            it("THEN completes the signalDeferred") {
                entry.signalDeferred.getCompleted() shouldBe AgentSignal.Done(DoneResult.COMPLETED)
            }
        }
    }

    // -------------------------------------------------------------------------
    // done — pass
    // -------------------------------------------------------------------------

    describe("GIVEN a registered session AND done signal with pass result") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry()
        sessionsState.register(guid, entry)
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(done, {handshakeGuid, result=pass})") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf(
                    "handshakeGuid" to guid.value,
                    "result" to ProtocolVocabulary.DoneResult.PASS,
                ),
            )

            it("THEN returns Success with AgentSignal.Done(PASS)") {
                result.shouldBeInstanceOf<DispatchResult.Success>()
                result.signal shouldBe AgentSignal.Done(DoneResult.PASS)
            }
        }
    }

    // -------------------------------------------------------------------------
    // done — needs_iteration
    // -------------------------------------------------------------------------

    describe("GIVEN a registered session AND done signal with needs_iteration result") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry()
        sessionsState.register(guid, entry)
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(done, {handshakeGuid, result=needs_iteration})") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf(
                    "handshakeGuid" to guid.value,
                    "result" to ProtocolVocabulary.DoneResult.NEEDS_ITERATION,
                ),
            )

            it("THEN returns Success with AgentSignal.Done(NEEDS_ITERATION)") {
                result.shouldBeInstanceOf<DispatchResult.Success>()
                result.signal shouldBe AgentSignal.Done(DoneResult.NEEDS_ITERATION)
            }
        }
    }

    // -------------------------------------------------------------------------
    // fail-workflow
    // -------------------------------------------------------------------------

    describe("GIVEN a registered session AND fail-workflow signal") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry()
        sessionsState.register(guid, entry)
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(fail-workflow, {handshakeGuid, reason})") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.FAIL_WORKFLOW,
                payload = mapOf("handshakeGuid" to guid.value, "reason" to "cannot proceed"),
            )

            it("THEN returns Success with AgentSignal.FailWorkflow") {
                result.shouldBeInstanceOf<DispatchResult.Success>()
                result.signal shouldBe AgentSignal.FailWorkflow("cannot proceed")
            }

            it("THEN completes the signalDeferred with FailWorkflow") {
                entry.signalDeferred.getCompleted() shouldBe AgentSignal.FailWorkflow("cannot proceed")
            }

            it("THEN updates lastActivityTimestamp") {
                entry.lastActivityTimestamp shouldBe fixedInstant
            }
        }
    }

    // -------------------------------------------------------------------------
    // lastActivityTimestamp — updated on every signal
    // -------------------------------------------------------------------------

    describe("GIVEN a registered session with old timestamp") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry()
        sessionsState.register(guid, entry)
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(self-compacted, {handshakeGuid})") {
            dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.SELF_COMPACTED,
                payload = mapOf("handshakeGuid" to guid.value),
            )

            it("THEN lastActivityTimestamp is updated to the fixed clock instant") {
                entry.lastActivityTimestamp shouldBe fixedInstant
            }
        }
    }

    // -------------------------------------------------------------------------
    // session not found
    // -------------------------------------------------------------------------

    describe("GIVEN empty SessionsState") {
        val sessionsState = SessionsState()
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)
        val unknownGuid = HandshakeGuid.generate()

        describe("WHEN dispatch with unknown handshakeGuid") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.SELF_COMPACTED,
                payload = mapOf("handshakeGuid" to unknownGuid.value),
            )

            it("THEN returns SessionNotFound").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                result.shouldBeInstanceOf<DispatchResult.SessionNotFound>()
            }

            it("THEN SessionNotFound contains the unknown guid").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                (result as DispatchResult.SessionNotFound).guid shouldBe unknownGuid
            }
        }
    }

    // -------------------------------------------------------------------------
    // bad request — missing handshakeGuid
    // -------------------------------------------------------------------------

    describe("GIVEN a dispatcher") {
        val sessionsState = SessionsState()
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch with missing handshakeGuid field") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf("result" to "completed"),
            )

            it("THEN returns BadRequest") {
                result.shouldBeInstanceOf<DispatchResult.BadRequest>()
            }

            it("THEN message mentions the missing field") {
                (result as DispatchResult.BadRequest).message shouldBe "missing_field: handshakeGuid"
            }
        }
    }

    // -------------------------------------------------------------------------
    // bad request — missing result for done
    // -------------------------------------------------------------------------

    describe("GIVEN a dispatcher AND done action without result field") {
        val sessionsState = SessionsState()
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(done, {handshakeGuid}) without result") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf("handshakeGuid" to "some-guid"),
            )

            it("THEN returns BadRequest for missing result") {
                result.shouldBeInstanceOf<DispatchResult.BadRequest>()
                (result as DispatchResult.BadRequest).message shouldBe "missing_field: result"
            }
        }
    }

    // -------------------------------------------------------------------------
    // bad request — invalid done result
    // -------------------------------------------------------------------------

    describe("GIVEN a dispatcher AND done action with invalid result") {
        val sessionsState = SessionsState()
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(done, {handshakeGuid, result=bogus})") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.DONE,
                payload = mapOf("handshakeGuid" to "some-guid", "result" to "bogus"),
            )

            it("THEN returns BadRequest for invalid result") {
                result.shouldBeInstanceOf<DispatchResult.BadRequest>()
                (result as DispatchResult.BadRequest).message shouldBe "invalid_done_result: bogus"
            }
        }
    }

    // -------------------------------------------------------------------------
    // bad request — missing reason for fail-workflow
    // -------------------------------------------------------------------------

    describe("GIVEN a dispatcher AND fail-workflow action without reason field") {
        val sessionsState = SessionsState()
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch(fail-workflow, {handshakeGuid}) without reason") {
            val result = dispatcher.dispatch(
                action = ProtocolVocabulary.Signal.FAIL_WORKFLOW,
                payload = mapOf("handshakeGuid" to "some-guid"),
            )

            it("THEN returns BadRequest for missing reason") {
                result.shouldBeInstanceOf<DispatchResult.BadRequest>()
                (result as DispatchResult.BadRequest).message shouldBe "missing_field: reason"
            }
        }
    }

    // -------------------------------------------------------------------------
    // bad request — unknown action
    // -------------------------------------------------------------------------

    describe("GIVEN a dispatcher AND unknown action") {
        val sessionsState = SessionsState()
        val dispatcher = SignalCallbackDispatcher(sessionsState, outFactory, fixedClock)

        describe("WHEN dispatch with unknown action") {
            val result = dispatcher.dispatch(
                action = "bogus-action",
                payload = mapOf("handshakeGuid" to "some-guid"),
            )

            it("THEN returns BadRequest for unknown action") {
                result.shouldBeInstanceOf<DispatchResult.BadRequest>()
                (result as DispatchResult.BadRequest).message shouldBe "unknown_action: bogus-action"
            }
        }
    }
})
