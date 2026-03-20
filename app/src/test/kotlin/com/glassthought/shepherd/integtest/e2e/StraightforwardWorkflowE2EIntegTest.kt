package com.glassthought.shepherd.integtest.e2e

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.bucket.isIntegTestEnabled
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
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
 * **GLM (Z.AI) note**: The binary detects `TICKET_SHEPHERD_GLM_ENABLED=true` and enables
 * [GlmConfig], which exports `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, and model alias
 * env vars explicitly in the `bash -c` command that tmux runs. This is necessary because
 * tmux sessions inherit from the tmux SERVER's environment, not the calling process's
 * environment — so env vars set on the subprocess ProcessBuilder do NOT propagate.
 *
 * Requires: Docker container, tmux, `Z_AI_GLM_API_TOKEN` secret, `-PrunIntegTests=true`.
 */
@OptIn(ExperimentalKotest::class)
class StraightforwardWorkflowE2EIntegTest : AsgardDescribeSpec(
    {
        describe("GIVEN a temp git repo with a simple ticket").config(isIntegTestEnabled()) {

            // ── Resolve paths ──────────────────────────────────────────────────
            // Gradle sets user.dir to the subproject directory (app/), so the repo
            // root is one level up.
            val appModuleDir = Path.of(System.getProperty("user.dir"))
            val repoRoot = appModuleDir.parent
            val binaryPath = repoRoot.resolve("app/build/install/app/bin/app")

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

            // ── Validate GLM token file exists (binary reads it via readZaiApiKey) ──
            val glmTokenFile = Path.of(myEnv, ".secrets", "Z_AI_GLM_API_TOKEN")
            require(Files.exists(glmTokenFile)) {
                "GLM token file not found at [$glmTokenFile]. Required for agent redirection to Z.AI."
            }

            // ── Allocate dynamic server port ─────────────────────────────────────
            val serverPort = ServerSocket(0).use { it.localPort }

            // ── Create temporary git repo under .tmp/ (per CLAUDE.md convention) ──
            val dotTmpDir = repoRoot.resolve(".tmp")
            Files.createDirectories(dotTmpDir)
            val tempDir = Files.createTempDirectory(dotTmpDir, "e2e-straightforward-")
            val tempDirFile = tempDir.toFile()

            // ── Pre-trust the temp directory in Claude CLI config ──────────────
            // WHY: Claude CLI shows an interactive trust dialog for unknown workspace
            // directories. In interactive mode (not --print), this blocks the agent
            // from proceeding. We pre-populate the project entry in .claude.json
            // so the trust dialog is skipped.
            preTrustWorkspace(tempDir)

            // Record tmux sessions before test for delta cleanup
            val tmuxSessionsBefore = listTmuxSessions()

            fun runGitInTemp(vararg args: String) {
                val process = ProcessBuilder("git", *args)
                    .directory(tempDirFile)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                require(exitCode == 0) {
                    "git ${args.joinToString(" ")} failed with exit code [$exitCode]. stderr=[$stderr]"
                }
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
                repoRoot.resolve("config/workflows/straightforward.json"),
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
                // Inherit PATH so git, tmux, claude are available.
                // WHY append ticket script directory: The harness calls `ticket close <id>` via
                // ProcessRunner (subprocess exec, not shell function). The `ticket` CLI lives at
                // $THORG_ROOT/submodules/note-ticket/ticket and is normally exposed via a shell
                // function, which ProcessBuilder cannot resolve. Adding its directory to PATH
                // makes the script directly executable.
                val basePath = System.getenv("PATH") ?: "/usr/bin:/bin"
                val thorgRoot = System.getenv("THORG_ROOT")
                val ticketDir = thorgRoot?.let { "$it/submodules/note-ticket" }
                put("PATH", if (ticketDir != null) "$basePath:$ticketDir" else basePath)
                put("HOME", System.getenv("HOME") ?: "/root")

                // THORG_ROOT may be needed by tools that reference it
                thorgRoot?.let { put("THORG_ROOT", it) }

                // Required by EnvironmentValidator (ref.ap.A8WqG9oplNTpsW7YqoIyX.E)
                put("HOST_USERNAME", hostUsername)
                put("TICKET_SHEPHERD_AGENTS_DIR", agentsDir)
                put("MY_ENV", myEnv)
                put("AI_MODEL__ZAI__FAST", aiModelZaiFast)

                // Server port for embedded HTTP server
                put("TICKET_SHEPHERD_SERVER_PORT", serverPort.toString())

                // Enable GLM so the binary exports ANTHROPIC_BASE_URL etc. into the tmux command.
                // WHY-NOT relying on env var inheritance: tmux sessions inherit from the tmux
                // SERVER's environment, not the calling process. Setting ANTHROPIC_BASE_URL on
                // this ProcessBuilder does NOT propagate to the spawned tmux session. Instead,
                // TICKET_SHEPHERD_GLM_ENABLED tells the binary to read the GLM token from the
                // secret file and build GlmConfig, which exports the env vars explicitly in the
                // bash -c command that tmux runs.
                put("TICKET_SHEPHERD_GLM_ENABLED", "true")

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
                // NOTE: --iteration-max is parsed by CLI but NOT consumed downstream (DEFERRED).
                // See ref.ap.mFo35x06vJbjMQ8m7Lh4Z.E in ShepherdInitializer.
                // Actual iteration limit is governed by the workflow JSON
                // (config/workflows/straightforward.json -> "iteration.max": 4).
                "--iteration-max", "1",
            )
                .directory(tempDirFile)
                .redirectOutput(stdoutFile)
                .redirectError(stderrFile)

            // Clear inherited env and set only what we need
            processBuilder.environment().clear()
            processBuilder.environment().putAll(subprocessEnv)

            val process = processBuilder.start()

            // ── Auto-advance Claude CLI onboarding prompts ────────────────────
            // WHY: The Claude CLI in interactive mode shows onboarding/trust
            // dialogs that block agent startup. We send Enter keys to the tmux
            // session to navigate past them. The tmux session name is known
            // because the binary uses a deterministic naming convention.
            val onboardingAdvancer = Thread {
                advancePastOnboardingPrompts(TMUX_SESSION_NAME)
            }.apply {
                isDaemon = true
                start()
            }

            val completed = process.waitFor(E2E_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            onboardingAdvancer.interrupt()
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

                // Delete temp directory only on success
                if (exitCode == 0) {
                    tempDirFile.deleteRecursively()
                } else {
                    println("[E2E] Keeping temp directory for debugging: ${tempDirFile.absolutePath}")
                }
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
        private const val DIAGNOSTIC_TAIL_LINES = 200

        /**
         * The tmux session name used by the binary for the doer agent.
         * Deterministic: derived from the workflow part name and role.
         */
        private const val TMUX_SESSION_NAME = "shepherd_main_impl"

        /**
         * Number of Enter keys to send to navigate past Claude CLI onboarding prompts.
         * Prompts: 1) theme selection, 2) security notes, 3) workspace trust.
         * Extra keys are harmless (they go to the agent's input prompt which ignores empty input).
         */
        private const val ONBOARDING_ENTER_COUNT = 5

        /**
         * Delay between Enter key presses (ms). Enough for the CLI to render the next prompt.
         */
        private const val ONBOARDING_KEY_DELAY_MS = 3_000L

        /**
         * Initial delay before sending keys (ms). Allows the tmux session and CLI to start up.
         */
        private const val ONBOARDING_INITIAL_DELAY_MS = 5_000L

        /**
         * Sends Enter keys to the tmux session to navigate past Claude CLI onboarding
         * prompts (theme selection, security notes, workspace trust dialog).
         *
         * WHY brute-force Enter keys: The Claude CLI in interactive mode shows these
         * prompts for new workspaces. There is no config setting or CLI flag to skip
         * them. Sending Enter accepts the default choice at each prompt. Extra Enter
         * keys are harmless -- they resolve to empty input at the agent prompt, which
         * the agent ignores.
         *
         * This method blocks (with sleeps) and should be called from a daemon thread.
         */
        private fun advancePastOnboardingPrompts(sessionName: String) {
            try {
                Thread.sleep(ONBOARDING_INITIAL_DELAY_MS)
                repeat(ONBOARDING_ENTER_COUNT) {
                    if (Thread.currentThread().isInterrupted) return
                    try {
                        ProcessBuilder("tmux", "send-keys", "-t", sessionName, "Enter")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start()
                            .waitFor()
                    } catch (_: Exception) {
                        // Session may not exist yet or already terminated
                    }
                    Thread.sleep(ONBOARDING_KEY_DELAY_MS)
                }
            } catch (_: InterruptedException) {
                // Expected when the main thread completes before we finish
            }
        }

        /**
         * Pre-populates workspace trust in Claude CLI's `.claude.json` so the interactive
         * trust dialog is skipped when the agent starts in the given workspace directory.
         *
         * WHY: Claude CLI (interactive mode) shows a trust dialog for unknown workspace
         * directories. In automated tests, no one is there to click "Yes, I trust this folder".
         * This writes the minimal project config that the CLI checks to skip the dialog.
         *
         * WHY-NOT using --print: The agent protocol requires interactive mode for tool use
         * and payload delivery.
         */
        private fun preTrustWorkspace(workspacePath: Path) {
            val home = Path.of(System.getenv("HOME") ?: "/home/node")
            val claudeJsonPath = home.resolve(".claude/.claude.json")
            require(Files.exists(claudeJsonPath)) {
                "Claude config not found at [$claudeJsonPath]. Cannot pre-trust workspace."
            }

            val mapper = ObjectMapper()
            val root = mapper.readTree(claudeJsonPath.toFile()) as ObjectNode

            val projects = root.get("projects") as? ObjectNode
                ?: mapper.createObjectNode().also { root.set<ObjectNode>("projects", it) }

            val absPath = workspacePath.toAbsolutePath().toString()
            if (!projects.has(absPath)) {
                val projectConfig = mapper.createObjectNode().apply {
                    putArray("allowedTools")
                    put("hasTrustDialogAccepted", true)
                    put("hasCompletedProjectOnboarding", true)
                }
                projects.set<ObjectNode>(absPath, projectConfig)
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(claudeJsonPath.toFile(), root)
        }

        /**
         * Lists currently running tmux session names.
         * Returns an empty list if tmux server is not running.
         */
        fun listTmuxSessions(): List<String> {
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
    }
}

