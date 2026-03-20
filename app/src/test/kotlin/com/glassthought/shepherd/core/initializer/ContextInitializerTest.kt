package com.glassthought.shepherd.core.initializer

import com.asgard.core.processRunner.ProcessRunner
import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.creator.ProcessRunnerFactory
import com.asgard.testTools.describe_spec.AsgardDescribeSpecConfig
import com.glassthought.shepherd.core.Constants
import com.glassthought.shepherd.core.agent.noninteractive.FakeProcessBehavior
import com.glassthought.shepherd.core.agent.noninteractive.FakeProcessRunner
import com.glassthought.shepherd.core.agent.noninteractive.NonInteractiveAgentRunner
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path

class ContextInitializerTest : AsgardDescribeSpec(
    config = AsgardDescribeSpecConfig(autoClearOutLinesAfterTest = true),
    body = {

    val testMyEnvDir = "/tmp/test-myenv"
    val testZaiApiKey = "test-zai-api-key-abc123"
    val zaiKeyRelativePath = ".secrets/Z_AI_GLM_API_TOKEN"

    /** Env var reader that returns all required env vars. */
    val allEnvVarsPresent: (String) -> String? = { envVarName ->
        when (envVarName) {
            Constants.REQUIRED_ENV_VARS.MY_ENV -> testMyEnvDir
            Constants.REQUIRED_ENV_VARS.HOST_USERNAME -> "testuser"
            Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR -> "/tmp/agents"
            Constants.REQUIRED_ENV_VARS.AI_MODEL_ZAI_FAST -> "test-fast-model"
            Constants.AGENT_COMM.SERVER_PORT_ENV_VAR -> "18080"
            else -> null
        }
    }

    /** File reader that returns the ZAI API key for the expected path. */
    val validFileReader: (Path) -> String = { path ->
        if (path == Path.of(testMyEnvDir, zaiKeyRelativePath)) {
            testZaiApiKey
        } else {
            throw java.io.FileNotFoundException("File not found: [$path]")
        }
    }

    /** Process runner factory that returns a fake. */
    val fakeProcessRunnerFactory = ProcessRunnerFactory {
        FakeProcessRunner(FakeProcessBehavior.Succeed(stdout = "ok"))
    }

    describe("GIVEN ContextInitializer with valid configuration") {
        val initializer = ContextInitializerImpl(
            envVarReader = allEnvVarsPresent,
            fileReader = validFileReader,
            processRunnerFactory = fakeProcessRunnerFactory,
        )

        describe("WHEN initialize is called") {
            it("THEN returns a ShepherdContext") {
                val context = initializer.initialize(outFactory)
                context shouldNotBe null
            }

            it("THEN ShepherdContext has a nonInteractiveAgentRunner") {
                val context = initializer.initialize(outFactory)
                context.nonInteractiveAgentRunner shouldNotBe null
            }

            it("THEN ShepherdContext has infra with outFactory") {
                val context = initializer.initialize(outFactory)
                context.infra.outFactory shouldNotBe null
            }
        }
    }

    describe("GIVEN ContextInitializer with missing MY_ENV") {
        val initializer = ContextInitializerImpl(
            envVarReader = { null },
            fileReader = validFileReader,
            processRunnerFactory = fakeProcessRunnerFactory,
        )

        describe("WHEN initialize is called") {
            it("THEN throws IllegalStateException mentioning MY_ENV") {
                val exception = shouldThrow<IllegalStateException> {
                    initializer.initialize(outFactory)
                }
                exception.message shouldContain Constants.REQUIRED_ENV_VARS.MY_ENV
            }
        }
    }

    describe("GIVEN ContextInitializer with missing ZAI API key file") {
        val fileReaderThrowingNotFound: (Path) -> String = { path ->
            throw java.io.FileNotFoundException("File not found: [$path]")
        }

        val initializer = ContextInitializerImpl(
            envVarReader = allEnvVarsPresent,
            fileReader = fileReaderThrowingNotFound,
            processRunnerFactory = fakeProcessRunnerFactory,
        )

        describe("WHEN initialize is called") {
            it("THEN throws IllegalStateException mentioning ZAI API key") {
                val exception = shouldThrow<IllegalStateException> {
                    initializer.initialize(outFactory)
                }
                exception.message shouldContain "ZAI API key"
            }
        }
    }

    describe("GIVEN ContextInitializer with empty ZAI API key file") {
        val emptyFileReader: (Path) -> String = { "" }

        val initializer = ContextInitializerImpl(
            envVarReader = allEnvVarsPresent,
            fileReader = emptyFileReader,
            processRunnerFactory = fakeProcessRunnerFactory,
        )

        describe("WHEN initialize is called") {
            it("THEN throws IllegalStateException mentioning empty key file") {
                val exception = shouldThrow<IllegalStateException> {
                    initializer.initialize(outFactory)
                }
                exception.message shouldContain "empty"
            }
        }
    }

    describe("GIVEN ContextInitializer with whitespace-only ZAI API key file") {
        val whitespaceFileReader: (Path) -> String = { "   \n  " }

        val initializer = ContextInitializerImpl(
            envVarReader = allEnvVarsPresent,
            fileReader = whitespaceFileReader,
            processRunnerFactory = fakeProcessRunnerFactory,
        )

        describe("WHEN initialize is called") {
            it("THEN throws IllegalStateException mentioning empty key file") {
                val exception = shouldThrow<IllegalStateException> {
                    initializer.initialize(outFactory)
                }
                exception.message shouldContain "empty"
            }
        }
    }
})
