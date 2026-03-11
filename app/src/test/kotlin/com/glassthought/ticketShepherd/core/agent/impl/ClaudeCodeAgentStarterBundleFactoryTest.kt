package com.glassthought.ticketShepherd.core.agent.impl

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.ticketShepherd.core.agent.data.StartAgentRequest
import com.glassthought.ticketShepherd.core.agent.starter.impl.ClaudeCodeAgentStarter
import com.glassthought.ticketShepherd.core.data.AgentType
import com.glassthought.ticketShepherd.core.data.PhaseType
import com.glassthought.ticketShepherd.core.initializer.data.Environment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path

class ClaudeCodeAgentStarterBundleFactoryTest : AsgardDescribeSpec({

    val testRequest = StartAgentRequest(
        phaseType = PhaseType.IMPLEMENTOR,
        workingDir = "/test/working/dir",
    )

    describe("GIVEN factory with test environment and a system prompt file") {
        val factory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = "/path/to/test-prompt.txt",
            claudeProjectsDir = Path.of("/fake/claude/projects"),
            outFactory = outFactory,
        )

        describe("WHEN create is called with CLAUDE_CODE") {
            val bundle = factory.create(AgentType.CLAUDE_CODE, testRequest)

            it("THEN starter is a ClaudeCodeAgentStarter") {
                bundle.starter.shouldBeInstanceOf<ClaudeCodeAgentStarter>()
            }

            it("THEN start command uses --system-prompt-file (not append)") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--system-prompt-file"
                val withoutAppend = command.replace("--append-system-prompt-file", "")
                withoutAppend shouldContain "--system-prompt-file"
            }

            it("THEN start command contains the file path") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "/path/to/test-prompt.txt"
            }

            it("THEN start command uses sonnet model") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--model sonnet"
            }

            it("THEN start command uses --tools with test tool set") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--tools Bash,Read,Write,Edit"
            }

            it("THEN start command includes --dangerously-skip-permissions") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--dangerously-skip-permissions"
            }

            it("THEN start command includes cd to working dir") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "cd /test/working/dir"
            }
        }
    }

    describe("GIVEN factory with production environment and a system prompt file") {
        val factory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.production(),
            systemPromptFilePath = "/path/to/prod-prompt.txt",
            claudeProjectsDir = Path.of("/fake/claude/projects"),
            outFactory = outFactory,
        )

        describe("WHEN create is called with CLAUDE_CODE") {
            val bundle = factory.create(AgentType.CLAUDE_CODE, testRequest)

            it("THEN start command uses --append-system-prompt-file") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--append-system-prompt-file"
            }

            it("THEN start command uses --tools with full production tool set") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--tools Bash,Edit,Read,Write,Glob,Grep"
            }
        }
    }

    describe("GIVEN factory with test environment and no system prompt file") {
        val factory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = null,
            claudeProjectsDir = Path.of("/fake/claude/projects"),
            outFactory = outFactory,
        )

        describe("WHEN create is called with PI agent type") {
            it("THEN throws IllegalArgumentException") {
                val exception = shouldThrow<IllegalArgumentException> {
                    factory.create(AgentType.PI, testRequest)
                }
                exception.message shouldContain "CLAUDE_CODE"
            }
        }

        describe("WHEN create is called with CLAUDE_CODE") {
            val bundle = factory.create(AgentType.CLAUDE_CODE, testRequest)

            it("THEN start command does not contain --system-prompt-file") {
                val command = bundle.starter.buildStartCommand().command
                command shouldNotContain "--system-prompt-file"
            }

            it("THEN start command uses sonnet model") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--model sonnet"
            }
        }
    }
})
