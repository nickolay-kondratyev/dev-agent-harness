package com.glassthought.bucket

import com.glassthought.ticketShepherd.core.agent.tmux.TmuxSession
import com.glassthought.ticketShepherd.integtest.SharedContextDescribeSpec
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe

/**
 * Tests for [com.glassthought.ticketShepherd.core.agent.tmux.TmuxSessionManager].
 *
 * Requires tmux to be installed on the system. Tests use bash sessions (not claude)
 * so they can run in CI environments.
 */
@OptIn(ExperimentalKotest::class)
class TmuxSessionManagerIntegTest : SharedContextDescribeSpec({

    describe("GIVEN TmuxSessionManager").config(isIntegTestEnabled()) {
        val sessionManager = chainsawContext.infra.tmux.sessionManager
        val createdSessions = mutableListOf<TmuxSession>()

        afterEach {
            // Clean up any sessions created during tests.
            createdSessions.forEach { session ->
                try {
                    sessionManager.killSession(session)
                } catch (_: Exception) {
                    // Session may already be killed by the test.
                }
            }
            createdSessions.clear()
        }

        describe("WHEN session is created") {
            it("THEN exists() returns true") {
                val sessionName = "test-exists-${System.currentTimeMillis()}"
                val session = sessionManager.createSession(sessionName, "bash")
                createdSessions.add(session)

                session.exists() shouldBe true
            }
        }

        describe("WHEN killSession is called on existing session") {
            it("THEN exists() returns false") {
                val sessionName = "test-kill-${System.currentTimeMillis()}"
                // Do NOT add to createdSessions since we kill it explicitly.
                val session = sessionManager.createSession(sessionName, "bash")
                sessionManager.killSession(session)

                session.exists() shouldBe false
            }
        }
    }
})
