package com.glassthought.chainsaw.core.agent.starter.impl

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
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
