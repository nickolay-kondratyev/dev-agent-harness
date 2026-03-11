package com.glassthought.chainsaw.core.agent.starter.impl

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ClaudeCodeAgentStarterTest : AsgardDescribeSpec({

    describe("GIVEN ClaudeCodeAgentStarter with all flags enabled") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/home/user/project",
            model = "sonnet",
            allowedTools = listOf("Read", "Write"),
            systemPrompt = "You are a test agent.",
            appendSystemPrompt = false,
            dangerouslySkipPermissions = true,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN command contains --model sonnet") {
                command shouldContain "--model sonnet"
            }

            it("THEN command contains --allowedTools Read,Write") {
                command shouldContain "--allowedTools Read,Write"
            }

            it("THEN command contains --system-prompt with the prompt text") {
                command shouldContain "--system-prompt"
                command shouldContain "You are a test agent."
            }

            it("THEN command contains --dangerously-skip-permissions") {
                command shouldContain "--dangerously-skip-permissions"
            }

            it("THEN command starts with bash -c and includes cd to working dir") {
                command shouldContain "bash -c 'cd /home/user/project && unset CLAUDECODE && claude"
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter with appendSystemPrompt=true") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/home/user/project",
            model = "opus",
            allowedTools = listOf("Bash", "Edit", "Read"),
            systemPrompt = "Additional context for the agent.",
            appendSystemPrompt = true,
            dangerouslySkipPermissions = true,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN command contains --append-system-prompt") {
                command shouldContain "--append-system-prompt"
                command shouldContain "Additional context for the agent."
            }

            it("THEN command does NOT contain bare --system-prompt (without append prefix)") {
                // Remove the append variant to check for the non-append variant
                val withoutAppend = command.replace("--append-system-prompt", "")
                withoutAppend shouldNotContain "--system-prompt"
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter without system prompt") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/tmp/test",
            model = "sonnet",
            allowedTools = listOf("Read"),
            systemPrompt = null,
            appendSystemPrompt = false,
            dangerouslySkipPermissions = false,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN command does not contain --system-prompt") {
                command shouldNotContain "--system-prompt"
            }

            it("THEN command does not contain --append-system-prompt") {
                command shouldNotContain "--append-system-prompt"
            }

            it("THEN command does not contain --dangerously-skip-permissions") {
                command shouldNotContain "--dangerously-skip-permissions"
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter with system prompt containing single quotes") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/home/user/project",
            model = "sonnet",
            allowedTools = listOf("Read", "Write"),
            systemPrompt = "You're a test agent. Don't do anything unexpected.",
            appendSystemPrompt = false,
            dangerouslySkipPermissions = true,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN single quotes in the prompt are escaped for the outer bash -c wrapper") {
                // The outer wrapper is: bash -c '<inner>'
                // Single quotes inside must use the end-quote, escaped-quote, start-quote idiom: '\''
                // So "You're" becomes "You'\''re" inside the outer single-quoted string.
                command shouldContain "You'\\''re"
                command shouldContain "Don'\\''t"
            }

            it("THEN the command is a valid bash -c wrapper with proper start and end quotes") {
                command.startsWith("bash -c '") shouldBe true
                command.endsWith("'") shouldBe true
            }

            it("THEN the prompt is still double-quoted within the inner command") {
                // The double quotes around the prompt text must survive the single-quote escaping
                command shouldContain "--system-prompt \""
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter with workingDir containing single quote") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/home/user/it's-a-project",
            model = "sonnet",
            allowedTools = listOf("Read"),
            systemPrompt = null,
            appendSystemPrompt = false,
            dangerouslySkipPermissions = true,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN single quote in workingDir is properly escaped") {
                command shouldContain "cd /home/user/it'\\''s-a-project"
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter with empty allowedTools") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/tmp/test",
            model = "sonnet",
            allowedTools = emptyList(),
            systemPrompt = null,
            appendSystemPrompt = false,
            dangerouslySkipPermissions = true,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN command does not contain --allowedTools") {
                command shouldNotContain "--allowedTools"
            }
        }
    }
})
