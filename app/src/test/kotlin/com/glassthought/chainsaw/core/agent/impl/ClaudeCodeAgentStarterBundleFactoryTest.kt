package com.glassthought.chainsaw.core.agent.impl

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.agent.starter.impl.ClaudeCodeAgentStarter
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.data.PhaseType
import com.glassthought.chainsaw.core.initializer.data.Environment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path

class ClaudeCodeAgentStarterBundleFactoryTest : AsgardDescribeSpec({

    val testRequest = StartAgentRequest(
        phaseType = PhaseType.IMPLEMENTOR,
        workingDir = "/test/working/dir",
    )

    describe("GIVEN factory with test environment") {
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
                command shouldContain "--system-prompt-file /path/to/test-prompt.txt"
            }

            it("THEN start command uses sonnet model") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--model sonnet"
            }

            it("THEN start command uses minimal allowed tools") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--allowedTools Read,Write"
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

    describe("GIVEN factory with production environment") {
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
                command shouldContain "--append-system-prompt-file /path/to/prod-prompt.txt"
            }

            it("THEN start command uses full allowed tools set") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--allowedTools Bash,Edit,Read,Write,Glob,Grep"
            }
        }
    }

    describe("GIVEN factory with test environment") {
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
    }

    describe("GIVEN factory with null systemPromptFilePath") {
        val factory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = null,
            claudeProjectsDir = Path.of("/fake/claude/projects"),
            outFactory = outFactory,
        )

        describe("WHEN create is called with CLAUDE_CODE") {
            val bundle = factory.create(AgentType.CLAUDE_CODE, testRequest)

            it("THEN start command has correct structure") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--model sonnet"
            }
        }
    }
})
