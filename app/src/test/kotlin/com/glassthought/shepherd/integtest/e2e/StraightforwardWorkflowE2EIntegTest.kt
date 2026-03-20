package com.glassthought.shepherd.integtest.e2e

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.bucket.isIntegTestEnabled
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * End-to-end black-box integration test for the straightforward workflow.
 *
 * Runs the actual `app` binary as a subprocess via [ProcessBuilder], pointing it at
 * a temporary git repo with a simple ticket. Validates that the harness:
 * 1. Exits with code 0 (full workflow completed: doer + reviewer PASS)
 * 2. Creates the `.ai_out/` directory structure
 * 3. Creates a feature branch in the temp repo
 *
 * This test does NOT assert agent coding quality (e.g., hello-world.sh content) --
 * it verifies **harness behavior**: orchestration, signal flow, git operations, and clean exit.
 *
 * **GLM (Z.AI) note**: The binary runs with `ContextInitializer.standard()` which does NOT
 * inject GLM env vars into the agent command. However, this test sets `ANTHROPIC_BASE_URL`,
 * `ANTHROPIC_AUTH_TOKEN`, and model alias env vars on the subprocess. These propagate via
 * environment inheritance: ProcessBuilder env -> binary process -> tmux session -> `claude` CLI.
 * If `ClaudeCodeAdapter.buildStartCommand()` ever adds `unset ANTHROPIC_BASE_URL`, this will break.
 *
 * Requires: Docker container, tmux, `Z_AI_GLM_API_TOKEN` secret, `-PrunIntegTests=true`.
 */
@OptIn(ExperimentalKotest::class)
class StraightforwardWorkflowE2EIntegTest : AsgardDescribeSpec(
    {
        describe("GIVEN a temp git repo with a simple ticket").config(isIntegTestEnabled()) {

            // ── Resolve binary path and fail hard if not built ───────────────────
            val projectRoot = Path.of(System.getProperty("user.dir"))
            val binaryPath = projectRoot.resolve("app/build/install/app/bin/app")

            require(Files.exists(binaryPath)) {
                "Binary not found at [$binaryPath]. Run `./gradlew :app:installDist` first."
            }

            // ── Read required env vars from current environment ──────────────────
            val myEnv = requireNotNull(System.getenv("MY_ENV")) {
                "MY_ENV env var is required for E2E integration tests."
            }
            val agentsDir = requireNotNull(System.getenv("TICKET_SHEPHERD_AGENTS_DIR")) {
                "TICKET_SHEPHERD_AGENTS_DIR env var is required for E2E integration tests."
            }
            val hostUsername = System.getenv("HOST_USERNAME") ?: "e2e-test"
            val aiModelZaiFast = System.getenv("AI_MODEL__ZAI__FAST") ?: "glm-4-flash"

            // ── Read GLM token for agent redirection ─────────────────────────────
            val glmTokenFile = Path.of(myEnv, ".secrets", "Z_AI_GLM_API_TOKEN")
            require(Files.exists(glmTokenFile)) {
                "GLM token file not found at [$glmTokenFile]. Required for agent redirection to Z.AI."
            }
            val glmToken = Files.readString(glmTokenFile).trim()

            // ── Allocate dynamic server port ─────────────────────────────────────
            val serverPort = ServerSocket(0).use { it.localPort }

            // ── Create temporary git repo ────────────────────────────────────────
            val tempDir = Files.createTempDirectory("shepherd-e2e-straightforward-")
            val tempDirFile = tempDir.toFile()

            // Record tmux sessions before test for delta cleanup
            val tmuxSessionsBefore = listTmuxSessions()

            fun runGitInTemp(vararg args: String): Int {
                val process = ProcessBuilder("git", *args)
                    .directory(tempDirFile)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                return process.waitFor()
            }

            // git init + configure user
            runGitInTemp("init")
            runGitInTemp("config", "user.name", "E2E Test")
            runGitInTemp("config", "user.email", "e2e@test.local")

            // Create ticket file with valid YAML frontmatter
            val ticketsDir = tempDir.resolve("_tickets")
            Files.createDirectories(ticketsDir)
            val ticketFile = ticketsDir.resolve("e2e-test-ticket.md")
            Files.writeString(
                ticketFile,
                """
                |---
                |id: e2e-test-straightforward
                |title: "E2E Test: Write hello-world.sh"
                |status: in_progress
                |type: task
                |priority: 2
                |---
                |
                |# Task
                |
                |Create a file called `hello-world.sh` that prints "Hello, World!" to stdout.
                |The file should be executable.
                """.trimMargin(),
            )

            // Create minimal CLAUDE.md in temp repo
            Files.writeString(
                tempDir.resolve("CLAUDE.md"),
                """
                |# E2E Test Repository
                |
                |You are in a test repository. Follow the ticket instructions exactly.
                |Create simple, minimal implementations. Do not over-engineer.
                """.trimMargin(),
            )

            // Copy workflow config to temp repo (binary resolves relative to working dir)
            val workflowDir = tempDir.resolve("config/workflows")
            Files.createDirectories(workflowDir)
            Files.copy(
                projectRoot.resolve("config/workflows/straightforward.json"),
                workflowDir.resolve("straightforward.json"),
            )

            // Initial commit so working tree is clean
            runGitInTemp("add", ".")
            runGitInTemp("commit", "-m", "Initial commit")

            // ── Stdout/stderr capture for debugging ──────────────────────────────
            val stdoutFile = tempDirFile.resolve("stdout.log")
            val stderrFile = tempDirFile.resolve("stderr.log")

            // ── Build environment for subprocess ─────────────────────────────────
            val subprocessEnv = buildMap {
                // Inherit PATH so git, tmux, claude are available
                put("PATH", System.getenv("PATH") ?: "/usr/bin:/bin")
                put("HOME", System.getenv("HOME") ?: "/root")

                // Required by EnvironmentValidator (ref.ap.A8WqG9oplNTpsW7YqoIyX.E)
                put("HOST_USERNAME", hostUsername)
                put("TICKET_SHEPHERD_AGENTS_DIR", agentsDir)
                put("MY_ENV", myEnv)
                put("AI_MODEL__ZAI__FAST", aiModelZaiFast)

                // Server port for embedded HTTP server
                put("TICKET_SHEPHERD_SERVER_PORT", serverPort.toString())

                // GLM env vars for agent redirection via environment inheritance.
                // The `claude` CLI inherits these through: ProcessBuilder -> binary -> tmux -> bash -c -> claude.
                put("ANTHROPIC_BASE_URL", "https://api.z.ai/api/anthropic")
                put("ANTHROPIC_AUTH_TOKEN", glmToken)
                put("CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "1")
                put("ANTHROPIC_DEFAULT_OPUS_MODEL", "glm-5")
                put("ANTHROPIC_DEFAULT_SONNET_MODEL", "glm-5")
                put("ANTHROPIC_DEFAULT_HAIKU_MODEL", "glm-4-flash")

                // Inherit JAVA_HOME if set (binary needs JVM)
                System.getenv("JAVA_HOME")?.let { put("JAVA_HOME", it) }

                // Inherit TERM for tmux
                System.getenv("TERM")?.let { put("TERM", it) }

                // Inherit XDG dirs for claude CLI config
                System.getenv("XDG_CONFIG_HOME")?.let { put("XDG_CONFIG_HOME", it) }
                System.getenv("XDG_DATA_HOME")?.let { put("XDG_DATA_HOME", it) }
            }

            // ── Execute binary ───────────────────────────────────────────────────
            val processBuilder = ProcessBuilder(
                binaryPath.toString(),
                "run",
                "--workflow", "straightforward",
                "--ticket", ticketFile.toString(),
                "--iteration-max", "1",
            )
                .directory(tempDirFile)
                .redirectOutput(stdoutFile)
                .redirectError(stderrFile)

            // Clear inherited env and set only what we need
            processBuilder.environment().clear()
            processBuilder.environment().putAll(subprocessEnv)

            val process = processBuilder.start()

            val completed = process.waitFor(E2E_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!completed) {
                process.destroyForcibly()
            }

            val exitCode = if (completed) process.exitValue() else TIMEOUT_EXIT_CODE

            // On failure, read logs for diagnostics
            val diagnosticMessage = if (exitCode != 0) {
                buildString {
                    appendLine("E2E test failed with exit code [$exitCode]")
                    appendLine("--- STDOUT (last 50 lines) ---")
                    if (stdoutFile.exists()) {
                        stdoutFile.readLines().takeLast(DIAGNOSTIC_TAIL_LINES).forEach { appendLine(it) }
                    }
                    appendLine("--- STDERR (last 50 lines) ---")
                    if (stderrFile.exists()) {
                        stderrFile.readLines().takeLast(DIAGNOSTIC_TAIL_LINES).forEach { appendLine(it) }
                    }
                }
            } else {
                ""
            }

            // ── Cleanup in afterSpec ─────────────────────────────────────────────
            afterSpec {
                // Kill tmux sessions created during the test (delta from before)
                val tmuxSessionsAfter = listTmuxSessions()
                val newSessions = tmuxSessionsAfter - tmuxSessionsBefore.toSet()
                newSessions.forEach { sessionName ->
                    try {
                        ProcessBuilder("tmux", "kill-session", "-t", sessionName)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start()
                            .waitFor()
                    } catch (_: Exception) {
                        // Best effort cleanup
                    }
                }

                // Delete temp directory
                tempDirFile.deleteRecursively()
            }

            // ── Assertions ───────────────────────────────────────────────────────
            describe("WHEN running shepherd with straightforward workflow") {

                it("THEN the process exits with code 0") {
                    io.kotest.assertions.withClue({ diagnosticMessage }) {
                        exitCode shouldBe 0
                    }
                }

                it("THEN .ai_out directory is created in the temp repo") {
                    val aiOutDir = tempDir.resolve(".ai_out").toFile()
                    io.kotest.assertions.withClue({
                        "Expected .ai_out/ directory at [${aiOutDir.absolutePath}]. $diagnosticMessage"
                    }) {
                        aiOutDir.exists() shouldBe true
                    }
                }

                it("THEN a feature branch was created (not on initial branch)") {
                    // The binary creates a feature branch. Verify we're not on the initial branch.
                    val branchProcess = ProcessBuilder("git", "branch", "--list")
                        .directory(tempDirFile)
                        .redirectErrorStream(true)
                        .start()
                    val branches = branchProcess.inputStream.bufferedReader().readLines()
                        .map { it.trim().removePrefix("* ") }
                        .filter { it.isNotBlank() }
                    branchProcess.waitFor()

                    // Should have more than just the initial branch (main/master)
                    branches.shouldNotBeEmpty()
                    val nonDefaultBranches = branches.filter { it != "main" && it != "master" }
                    nonDefaultBranches.shouldNotBeEmpty()
                }
            }
        }
    },
    AsgardDescribeSpecConfig.FOR_INTEG_TEST,
) {
    companion object {
        private const val E2E_TIMEOUT_MINUTES = 15L
        private const val TIMEOUT_EXIT_CODE = -1
        private const val DIAGNOSTIC_TAIL_LINES = 50
    }
}

/**
 * Lists currently running tmux session names.
 * Returns an empty list if tmux server is not running.
 */
private fun listTmuxSessions(): List<String> {
    return try {
        val process = ProcessBuilder("tmux", "list-sessions", "-F", "#{session_name}")
            .redirectErrorStream(true)
            .start()
        val sessions = process.inputStream.bufferedReader().readLines()
            .filter { it.isNotBlank() }
        process.waitFor()
        sessions
    } catch (_: Exception) {
        emptyList()
    }
}

