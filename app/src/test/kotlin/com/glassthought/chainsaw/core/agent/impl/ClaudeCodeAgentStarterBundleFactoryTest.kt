package com.glassthought.chainsaw.core.agent.impl

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.agent.starter.impl.ClaudeCodeAgentStarter
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.data.PhaseType
import com.glassthought.chainsaw.core.initializer.data.Environment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Path

class ClaudeCodeAgentStarterBundleFactoryTest : AsgardDescribeSpec({

    val testRequest = StartAgentRequest(
        phaseType = PhaseType.IMPLEMENTOR,
        workingDir = "/test/working/dir",
    )

    describe("GIVEN factory with test environment and a system prompt file") {
        val promptFile = createTempPromptFile("You are a test agent.")

        val factory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.test(),
            systemPromptFilePath = promptFile.absolutePath,
            claudeProjectsDir = Path.of("/fake/claude/projects"),
            outFactory = outFactory,
        )

        afterSpec {
            promptFile.delete()
        }

        describe("WHEN create is called with CLAUDE_CODE") {
            val bundle = factory.create(AgentType.CLAUDE_CODE, testRequest)

            it("THEN starter is a ClaudeCodeAgentStarter") {
                bundle.starter.shouldBeInstanceOf<ClaudeCodeAgentStarter>()
            }

            it("THEN start command uses --system-prompt (not append)") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--system-prompt"
                // Ensure it is NOT --append-system-prompt
                val withoutAppend = command.replace("--append-system-prompt", "")
                withoutAppend shouldContain "--system-prompt"
            }

            it("THEN start command contains the prompt text") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "You are a test agent."
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

    describe("GIVEN factory with production environment and a system prompt file") {
        val promptFile = createTempPromptFile("Production system prompt.")

        val factory = ClaudeCodeAgentStarterBundleFactory(
            environment = Environment.production(),
            systemPromptFilePath = promptFile.absolutePath,
            claudeProjectsDir = Path.of("/fake/claude/projects"),
            outFactory = outFactory,
        )

        afterSpec {
            promptFile.delete()
        }

        describe("WHEN create is called with CLAUDE_CODE") {
            val bundle = factory.create(AgentType.CLAUDE_CODE, testRequest)

            it("THEN start command uses --append-system-prompt") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--append-system-prompt"
            }

            it("THEN start command uses full allowed tools set") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--allowedTools Bash,Edit,Read,Write,Glob,Grep"
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

            it("THEN start command does not contain --system-prompt") {
                val command = bundle.starter.buildStartCommand().command
                command shouldNotContain "--system-prompt"
            }

            it("THEN start command uses sonnet model") {
                val command = bundle.starter.buildStartCommand().command
                command shouldContain "--model sonnet"
            }
        }
    }
})

/**
 * Creates a temporary file with the given prompt content for testing.
 */
private fun createTempPromptFile(content: String): File {
    val file = File.createTempFile("test-prompt-", ".txt")
    file.writeText(content)
    return file
}
