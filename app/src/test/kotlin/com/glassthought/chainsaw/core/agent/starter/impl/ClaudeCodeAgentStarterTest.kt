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
            systemPromptFilePath = "/path/to/prompt.txt",
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

            it("THEN command contains --system-prompt-file") {
                command shouldContain "--system-prompt-file /path/to/prompt.txt"
            }

            it("THEN command contains --dangerously-skip-permissions") {
                command shouldContain "--dangerously-skip-permissions"
            }

            it("THEN command starts with bash -c and includes cd to working dir") {
                command shouldContain "bash -c 'cd /home/user/project && claude"
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter with appendSystemPrompt=true") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/home/user/project",
            model = "opus",
            allowedTools = listOf("Bash", "Edit", "Read"),
            systemPromptFilePath = "/path/to/prod-prompt.txt",
            appendSystemPrompt = true,
            dangerouslySkipPermissions = true,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN command contains --append-system-prompt-file") {
                command shouldContain "--append-system-prompt-file /path/to/prod-prompt.txt"
            }

            it("THEN command does NOT contain --system-prompt-file (without append prefix)") {
                // Remove the append variant to check for the non-append variant
                val withoutAppend = command.replace("--append-system-prompt-file", "")
                withoutAppend shouldNotContain "--system-prompt-file"
            }
        }
    }

    describe("GIVEN ClaudeCodeAgentStarter without system prompt file") {
        val starter = ClaudeCodeAgentStarter(
            workingDir = "/tmp/test",
            model = "sonnet",
            allowedTools = listOf("Read"),
            systemPromptFilePath = null,
            appendSystemPrompt = false,
            dangerouslySkipPermissions = false,
        )

        describe("WHEN buildStartCommand is called") {
            val command = starter.buildStartCommand().command

            it("THEN command does not contain --system-prompt-file") {
                command shouldNotContain "--system-prompt-file"
            }

            it("THEN command does not contain --append-system-prompt-file") {
                command shouldNotContain "--append-system-prompt-file"
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
            systemPromptFilePath = null,
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
