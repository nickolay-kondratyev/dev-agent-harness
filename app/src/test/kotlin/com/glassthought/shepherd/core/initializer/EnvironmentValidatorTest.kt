package com.glassthought.shepherd.core.initializer

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.Constants
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class EnvironmentValidatorTest : AsgardDescribeSpec({

    // A path that will never exist — used to simulate "not in Docker".
    val nonExistentDockerEnvPath = Path.of("/tmp/nonexistent_dockerenv_sentinel_${System.nanoTime()}")

    // A real temp file — simulates "in Docker" without relying on /.dockerenv existing.
    val existingDockerEnvFile: Path = Files.createTempFile("dockerenv_sentinel_", null)
        .also { it.toFile().deleteOnExit() }

    // An env var reader that returns values for all required env vars.
    val allEnvVarsPresent: (String) -> String? = { envVarName ->
        when (envVarName) {
            Constants.REQUIRED_ENV_VARS.HOST_USERNAME -> "testuser"
            Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR -> "/tmp/agents"
            Constants.REQUIRED_ENV_VARS.MY_ENV -> "/tmp/myenv"
            else -> null
        }
    }

    describe("GIVEN EnvironmentValidator running outside Docker") {
        val validator = EnvironmentValidatorImpl(
            dockerEnvFilePath = nonExistentDockerEnvPath,
            envVarReader = allEnvVarsPresent,
        )

        describe("WHEN validate is called") {
            it("THEN throws IllegalStateException with Docker message") {
                val exception = shouldThrow<IllegalStateException> {
                    validator.validate()
                }
                exception.message shouldContain "Docker container"
                exception.message shouldContain "--dangerously-skip-permissions"
            }
        }
    }

    describe("GIVEN EnvironmentValidator running in Docker with all env vars present") {
        val validator = EnvironmentValidatorImpl(
            dockerEnvFilePath = existingDockerEnvFile,
            envVarReader = allEnvVarsPresent,
        )

        describe("WHEN validate is called") {
            it("THEN does not throw") {
                validator.validate()
            }
        }
    }

    describe("GIVEN EnvironmentValidator with missing env vars") {
        val validator = EnvironmentValidatorImpl(
            dockerEnvFilePath = existingDockerEnvFile,
            envVarReader = { null },
        )

        describe("WHEN validate is called") {
            it("THEN throws IllegalStateException listing all missing env vars") {
                val exception = shouldThrow<IllegalStateException> {
                    validator.validate()
                }
                exception.message shouldContain Constants.REQUIRED_ENV_VARS.HOST_USERNAME
                exception.message shouldContain Constants.REQUIRED_ENV_VARS.TICKET_SHEPHERD_AGENTS_DIR
                exception.message shouldContain Constants.REQUIRED_ENV_VARS.MY_ENV
            }
        }
    }

    describe("GIVEN EnvironmentValidator with one blank env var") {
        val validatorWithBlank = EnvironmentValidatorImpl(
            dockerEnvFilePath = existingDockerEnvFile,
            envVarReader = { envVarName ->
                if (envVarName == Constants.REQUIRED_ENV_VARS.HOST_USERNAME) "   "
                else allEnvVarsPresent(envVarName)
            },
        )

        describe("WHEN validate is called") {
            it("THEN throws IllegalStateException listing the blank env var") {
                val exception = shouldThrow<IllegalStateException> {
                    validatorWithBlank.validate()
                }
                exception.message shouldContain Constants.REQUIRED_ENV_VARS.HOST_USERNAME
            }
        }
    }
})
