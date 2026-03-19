package com.glassthought.shepherd.core.agent.noninteractive

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.data.AgentType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class NonInteractiveAgentRunnerImplTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    val testZaiApiKey = "test-zai-key-123"
    val testWorkDir = Path.of("/tmp/test-work-dir")
    val testModel = "sonnet"
    val testInstructions = "do something useful"
    val testTimeout = 20.minutes

    fun buildRequest(
        agentType: AgentType = AgentType.CLAUDE_CODE,
        instructions: String = testInstructions,
        model: String = testModel,
        workingDirectory: Path = testWorkDir,
        timeout: kotlin.time.Duration = testTimeout,
    ) = NonInteractiveAgentRequest(
        instructions = instructions,
        workingDirectory = workingDirectory,
        agentType = agentType,
        model = model,
        timeout = timeout,
    )

    fun buildRunner(behavior: FakeProcessBehavior): Pair<NonInteractiveAgentRunnerImpl, FakeProcessRunner> {
        val fakeProcessRunner = FakeProcessRunner(behavior)
        val runner = NonInteractiveAgentRunnerImpl(
            processRunner = fakeProcessRunner,
            outFactory = outFactory,
            zaiApiKey = testZaiApiKey,
        )
        return runner to fakeProcessRunner
    }

    describe("GIVEN a CLAUDE_CODE agent request") {
        describe("WHEN run is called") {
            val (runner, fake) = buildRunner(FakeProcessBehavior.Succeed(stdout = "ok"))

            it("THEN the shell command contains claude --print") {
                runner.run(buildRequest(agentType = AgentType.CLAUDE_CODE))
                val command = fake.lastCommandArgs!!
                command[0] shouldBe "bash"
                command[1] shouldBe "-c"
                command[2] shouldContain "claude --print"
            }
        }

        describe("AND the command is constructed") {
            val runner = buildRunner(FakeProcessBehavior.Succeed(stdout = "ok")).first

            it("THEN it starts with cd to working directory") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.CLAUDE_CODE))
                cmd shouldContain "cd '/tmp/test-work-dir'"
            }

            it("THEN it includes --model with the requested model") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.CLAUDE_CODE))
                cmd shouldContain "--model 'sonnet'"
            }

            it("THEN it includes -p with the instructions") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.CLAUDE_CODE))
                cmd shouldContain "-p 'do something useful'"
            }

            it("THEN it does NOT contain ZAI_API_KEY") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.CLAUDE_CODE))
                (cmd.contains("ZAI_API_KEY")) shouldBe false
            }
        }
    }

    describe("GIVEN a PI agent request") {
        describe("AND the command is constructed") {
            val runner = buildRunner(FakeProcessBehavior.Succeed(stdout = "ok")).first

            it("THEN it starts with cd to working directory") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.PI))
                cmd shouldContain "cd '/tmp/test-work-dir'"
            }

            it("THEN it exports ZAI_API_KEY") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.PI))
                cmd shouldContain "export ZAI_API_KEY='test-zai-key-123'"
            }

            it("THEN it includes pi --provider zai") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.PI))
                cmd shouldContain "pi --provider zai"
            }

            it("THEN it includes --model with the requested model") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.PI))
                cmd shouldContain "--model 'sonnet'"
            }

            it("THEN it includes -p with the instructions") {
                val cmd = runner.buildShellCommand(buildRequest(agentType = AgentType.PI))
                cmd shouldContain "-p 'do something useful'"
            }
        }
    }

    describe("GIVEN the agent process succeeds") {
        describe("WHEN run is called") {
            val (runner, _) = buildRunner(
                FakeProcessBehavior.Succeed(stdout = "task completed", stderr = "")
            )

            it("THEN returns Success result") {
                val result = runner.run(buildRequest())
                (result is NonInteractiveAgentResult.Success) shouldBe true
            }

            it("THEN output contains stdout") {
                val result = runner.run(buildRequest())
                (result as NonInteractiveAgentResult.Success).output shouldBe "task completed"
            }
        }
    }

    describe("GIVEN the agent process succeeds with both stdout and stderr") {
        describe("WHEN run is called") {
            val (runner, _) = buildRunner(
                FakeProcessBehavior.Succeed(stdout = "output text", stderr = "warning text")
            )

            it("THEN output combines stdout and stderr") {
                val result = runner.run(buildRequest())
                (result as NonInteractiveAgentResult.Success).output shouldBe "output text\nwarning text"
            }
        }
    }

    describe("GIVEN the agent process fails with non-zero exit code") {
        describe("WHEN run is called") {
            val (runner, _) = buildRunner(
                FakeProcessBehavior.Fail(exitCode = 1, stdout = "partial output", stderr = "error details")
            )

            it("THEN returns Failed result") {
                val result = runner.run(buildRequest())
                (result is NonInteractiveAgentResult.Failed) shouldBe true
            }

            it("THEN exitCode matches the process exit code") {
                val result = runner.run(buildRequest())
                (result as NonInteractiveAgentResult.Failed).exitCode shouldBe 1
            }

            it("THEN output combines stdout and stderr") {
                val result = runner.run(buildRequest())
                (result as NonInteractiveAgentResult.Failed).output shouldBe "partial output\nerror details"
            }
        }
    }

    describe("GIVEN the agent process times out") {
        describe("WHEN run is called") {
            val (runner, _) = buildRunner(
                FakeProcessBehavior.Timeout(stdout = "partial stdout", stderr = "partial stderr")
            )

            it("THEN returns TimedOut result") {
                val result = runner.run(buildRequest())
                (result is NonInteractiveAgentResult.TimedOut) shouldBe true
            }

            it("THEN output combines stdout and stderr") {
                val result = runner.run(buildRequest())
                (result as NonInteractiveAgentResult.TimedOut).output shouldBe "partial stdout\npartial stderr"
            }
        }
    }

    describe("GIVEN instructions with single quotes") {
        describe("WHEN the command is constructed") {
            val runner = buildRunner(FakeProcessBehavior.Succeed(stdout = "ok")).first
            val request = buildRequest(instructions = "fix the user's file")

            it("THEN single quotes are properly escaped") {
                val cmd = runner.buildShellCommand(request)
                cmd shouldContain "'fix the user'\\''s file'"
            }
        }
    }
})
