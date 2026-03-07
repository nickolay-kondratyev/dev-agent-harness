package org.example

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.tmux.TmuxSessionManager
import com.glassthought.tmux.data.TmuxSessionName
import com.glassthought.tmux.util.TmuxCommandRunner
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe

/**
 * Tests for [com.glassthought.tmux.TmuxSessionManager].
 *
 * Requires tmux to be installed on the system. Tests use bash sessions (not claude)
 * so they can run in CI environments.
 */
@OptIn(ExperimentalKotest::class)
class TmuxSessionManagerTest : AsgardDescribeSpec({

    describe("GIVEN TmuxSessionManager").config(isIntegTestEnabled()) {
        val commandRunner = TmuxCommandRunner()
        val sessionManager = TmuxSessionManager(outFactory, commandRunner)
        val createdSessions = mutableListOf<TmuxSessionName>()

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

        describe("WHEN createSession with bash") {
            it("THEN session exists") {
                val sessionName = "test-session-${System.currentTimeMillis()}"
                val session = sessionManager.createSession(sessionName, "bash")
                createdSessions.add(session)

                sessionManager.sessionExists(sessionName) shouldBe true
            }
        }

        describe("WHEN sessionExists with existing session") {
            it("THEN returns true") {
                val sessionName = "test-exists-${System.currentTimeMillis()}"
                val session = sessionManager.createSession(sessionName, "bash")
                createdSessions.add(session)

                sessionManager.sessionExists(sessionName) shouldBe true
            }
        }

        describe("WHEN sessionExists with non-existent name") {
            it("THEN returns false") {
                val nonExistentName = "non-existent-session-${System.currentTimeMillis()}"
                sessionManager.sessionExists(nonExistentName) shouldBe false
            }
        }

        describe("WHEN killSession is called on existing session") {
            it("THEN session no longer exists") {
                val sessionName = "test-kill-${System.currentTimeMillis()}"
                // Do NOT add to createdSessions since we kill it explicitly.
                val session = sessionManager.createSession(sessionName, "bash")
                sessionManager.killSession(session)

                sessionManager.sessionExists(sessionName) shouldBe false
            }
        }
    }
})
