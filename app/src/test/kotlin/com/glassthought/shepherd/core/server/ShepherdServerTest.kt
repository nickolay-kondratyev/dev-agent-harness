package com.glassthought.shepherd.core.server

import com.asgard.core.out.LogLevel
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.agent.facade.AgentSignal
import com.glassthought.shepherd.core.agent.facade.DoneResult
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import com.glassthought.shepherd.core.session.SessionsState
import com.glassthought.shepherd.core.session.createTestSessionEntry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Instant

class ShepherdServerTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    // ── malformed JSON ─────────────────────────────────────────────────

    describe("GIVEN a registered session") {
        describe("WHEN POST /signal/done with missing required field") {
            it("THEN returns 400 (not 500)") {
                val (sessionsState, _) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"some-guid"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    // ── /signal/done ───────────────────────────────────────────────────

    describe("GIVEN a registered DOER session") {
        describe("WHEN POST /signal/done with result=completed") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN signalDeferred is completed with Done(COMPLETED)") {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }
                }

                val signal = entry.signalDeferred.getCompleted()
                signal.shouldBeInstanceOf<AgentSignal.Done>()
                signal.result shouldBe DoneResult.COMPLETED
            }
        }

        describe("WHEN POST /signal/done with result=pass (role mismatch)") {
            it("THEN returns 400").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"pass"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        describe("WHEN POST /signal/done with invalid result=banana") {
            it("THEN returns 400").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"banana"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    describe("GIVEN a registered REVIEWER session") {
        describe("WHEN POST /signal/done with result=pass") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerReviewerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"pass"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN signalDeferred is completed with Done(PASS)") {
                val (sessionsState, guid) = registerReviewerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"pass"}""")
                    }
                }

                val signal = entry.signalDeferred.getCompleted()
                signal.shouldBeInstanceOf<AgentSignal.Done>()
                signal.result shouldBe DoneResult.PASS
            }
        }

        describe("WHEN POST /signal/done with result=needs_iteration") {
            it("THEN signalDeferred is completed with Done(NEEDS_ITERATION)") {
                val (sessionsState, guid) = registerReviewerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"needs_iteration"}""")
                    }
                }

                val signal = entry.signalDeferred.getCompleted()
                signal.shouldBeInstanceOf<AgentSignal.Done>()
                signal.result shouldBe DoneResult.NEEDS_ITERATION
            }
        }

        describe("WHEN POST /signal/done with result=completed (role mismatch)") {
            it("THEN returns 400").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerReviewerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    describe("GIVEN no session is registered") {
        describe("WHEN POST /signal/done with unknown GUID") {
            it("THEN returns 404").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val sessionsState = SessionsState()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"handshake.unknown","result":"completed"}""")
                    }
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }

    describe("GIVEN a DOER session whose deferred is already completed") {
        describe("WHEN POST /signal/done is sent again (duplicate)") {
            it("THEN returns 200 (idempotent)").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }

                    client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }

                    val response = client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    }

    // ── /signal/fail-workflow ──────────────────────────────────────────

    describe("GIVEN a registered session") {
        describe("WHEN POST /signal/fail-workflow") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/fail-workflow") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","reason":"cannot parse"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN signalDeferred is completed with FailWorkflow") {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/fail-workflow") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","reason":"cannot parse"}""")
                    }
                }

                val signal = entry.signalDeferred.getCompleted()
                signal.shouldBeInstanceOf<AgentSignal.FailWorkflow>()
                signal.reason shouldBe "cannot parse"
            }
        }
    }

    describe("GIVEN a session whose deferred was already completed by done") {
        describe("WHEN POST /signal/fail-workflow (late fail after done)") {
            it("THEN returns 200 (idempotent)").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.ERROR)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }

                    client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }

                    val response = client.post("/callback-shepherd/signal/fail-workflow") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","reason":"too late"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN signalDeferred retains the original Done signal").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.ERROR)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }

                    client.post("/callback-shepherd/signal/done") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","result":"completed"}""")
                    }

                    client.post("/callback-shepherd/signal/fail-workflow") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","reason":"too late"}""")
                    }
                }

                val signal = entry.signalDeferred.getCompleted()
                signal.shouldBeInstanceOf<AgentSignal.Done>()
            }
        }
    }

    // ── /signal/started ───────────────────────────────────────────────

    describe("GIVEN a registered session with an old timestamp") {
        describe("WHEN POST /signal/started") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/started") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN lastActivityTimestamp is updated") {
                val (sessionsState, guid) = registerDoerSessionWithOldTimestamp()
                val entry = sessionsState.lookupBlocking(guid)
                val before = entry.lastActivityTimestamp.get()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/started") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }
                }

                val after = entry.lastActivityTimestamp.get()
                (after > before) shouldBe true
            }
        }
    }

    // ── /signal/user-question ──────────────────────────────────────────

    describe("GIVEN a registered session with empty question queue") {
        describe("WHEN POST /signal/user-question") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/user-question") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","question":"API key?"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN question is appended to questionQueue") {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/user-question") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","question":"API key?"}""")
                    }
                }

                entry.questionQueue.size shouldBe 1
                entry.questionQueue.peek().question shouldBe "API key?"
            }
        }
    }

    // ── /signal/self-compacted ─────────────────────────────────────────

    describe("GIVEN a registered session for self-compacted") {
        describe("WHEN POST /signal/self-compacted") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/self-compacted") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN signalDeferred is completed with SelfCompacted") {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/self-compacted") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }
                }

                entry.signalDeferred.getCompleted() shouldBe AgentSignal.SelfCompacted
            }
        }
    }

    describe("GIVEN a session whose deferred was already completed by self-compacted") {
        describe("WHEN POST /signal/self-compacted is sent again (duplicate)") {
            it("THEN returns 200 (idempotent)").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }

                    client.post("/callback-shepherd/signal/self-compacted") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }

                    val response = client.post("/callback-shepherd/signal/self-compacted") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN signalDeferred retains the original SelfCompacted signal").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }

                    client.post("/callback-shepherd/signal/self-compacted") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }

                    client.post("/callback-shepherd/signal/self-compacted") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}"}""")
                    }
                }

                entry.signalDeferred.getCompleted() shouldBe AgentSignal.SelfCompacted
            }
        }
    }

    // ── /signal/ack-payload ───────────────────────────────────────────

    describe("GIVEN a session with a pending payload ACK") {
        describe("WHEN POST /signal/ack-payload with matching payloadId") {
            it("THEN returns 200") {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                entry.pendingPayloadAck.set(PayloadId("abc-1"))
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/ack-payload") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","payloadId":"abc-1"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN pendingPayloadAck is cleared to null") {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                entry.pendingPayloadAck.set(PayloadId("abc-1"))
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/ack-payload") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","payloadId":"abc-1"}""")
                    }
                }

                entry.pendingPayloadAck.get() shouldBe null
            }
        }

        describe("WHEN POST /signal/ack-payload with mismatched payloadId") {
            it("THEN returns 200").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                entry.pendingPayloadAck.set(PayloadId("abc-1"))
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/ack-payload") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","payloadId":"wrong"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            it("THEN pendingPayloadAck is NOT cleared").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val entry = sessionsState.lookupBlocking(guid)
                entry.pendingPayloadAck.set(PayloadId("abc-1"))
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    client.post("/callback-shepherd/signal/ack-payload") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","payloadId":"wrong"}""")
                    }
                }

                entry.pendingPayloadAck.get() shouldBe PayloadId("abc-1")
            }
        }
    }

    describe("GIVEN a session with null pendingPayloadAck") {
        describe("WHEN POST /signal/ack-payload (duplicate ACK)") {
            it("THEN returns 200").config(
                extensions = listOf(logCheckOverrideAllow(LogLevel.WARN)),
            ) {
                val (sessionsState, guid) = registerDoerSession()
                val server = ShepherdServer(sessionsState, outFactory)

                testApplication {
                    application { server.configureApplication(this) }
                    val response = client.post("/callback-shepherd/signal/ack-payload") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"handshakeGuid":"${guid.value}","payloadId":"x"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                }
            }
        }
    }
})

// ── Test helpers ────────────────────────────────────────────────────

private data class RegisteredSession(
    val sessionsState: SessionsState,
    val guid: HandshakeGuid,
)

private suspend fun registerDoerSession(): RegisteredSession {
    val sessionsState = SessionsState()
    val guid = HandshakeGuid.generate()
    val entry = createTestSessionEntry(subPartIndex = 0)
    sessionsState.register(guid, entry)
    return RegisteredSession(sessionsState, guid)
}

private suspend fun registerDoerSessionWithOldTimestamp(): RegisteredSession {
    val sessionsState = SessionsState()
    val guid = HandshakeGuid.generate()
    val entry = createTestSessionEntry(subPartIndex = 0)
    entry.lastActivityTimestamp.set(Instant.EPOCH)
    sessionsState.register(guid, entry)
    return RegisteredSession(sessionsState, guid)
}

private suspend fun registerReviewerSession(): RegisteredSession {
    val sessionsState = SessionsState()
    val guid = HandshakeGuid.generate()
    val entry = createTestSessionEntry(subPartIndex = 1)
    sessionsState.register(guid, entry)
    return RegisteredSession(sessionsState, guid)
}

/** Lookup that fails hard if not found -- only for test assertions. */
private suspend fun SessionsState.lookupBlocking(
    guid: HandshakeGuid,
): com.glassthought.shepherd.core.session.SessionEntry =
    lookup(guid) ?: error("Session not found for guid=[$guid]")
