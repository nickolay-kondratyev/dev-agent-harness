package com.glassthought.shepherd.core.initializer

import com.asgard.core.annotation.AnchorPoint
import com.glassthought.shepherd.core.Constants
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates the runtime environment before any infrastructure is created.
 *
 * Called as the very first step in `main()` — before [ContextInitializer],
 * before `runBlocking`, before anything else.
 *
 * Validates:
 * 1. **Docker**: The process is running inside a Docker container (checks `/.dockerenv`).
 *    This is a hard requirement because agents are spawned with `--dangerously-skip-permissions`.
 * 2. **Required env vars**: All [Constants.REQUIRED_ENV_VARS.ALL] are present and non-blank.
 */
@AnchorPoint("ap.A8WqG9oplNTpsW7YqoIyX.E")
fun interface EnvironmentValidator {
  /**
   * Validates the runtime environment. Throws [IllegalStateException] on failure.
   * Non-suspend — runs before any coroutine infrastructure.
   */
  fun validate()

  companion object {
    fun standard(): EnvironmentValidator = EnvironmentValidatorImpl()
  }
}

/**
 * @param dockerEnvFilePath Path to the Docker sentinel file. Default: `/.dockerenv`.
 *   Injectable for unit testing without touching the real filesystem.
 * @param envVarReader Function to read environment variables. Default: [System.getenv].
 *   Injectable for unit testing without depending on real env vars.
 */
class EnvironmentValidatorImpl(
  private val dockerEnvFilePath: Path = Path.of("/.dockerenv"),
  private val envVarReader: (String) -> String? = System::getenv,
) : EnvironmentValidator {

  override fun validate() {
    validateDockerEnvironment()
    validateRequiredEnvVars()
  }

  private fun validateDockerEnvironment() {
    check(Files.exists(dockerEnvFilePath)) {
      "TICKET_SHEPHERD must run inside a Docker container. " +
        "Docker sentinel file not found at [$dockerEnvFilePath]. " +
        "Agents are spawned with --dangerously-skip-permissions which is only safe inside a container."
    }
  }

  private fun validateRequiredEnvVars() {
    val missing = Constants.REQUIRED_ENV_VARS.ALL.filter { envVarName ->
      envVarReader(envVarName).isNullOrBlank()
    }

    check(missing.isEmpty()) {
      "Required environment variables are missing or blank: $missing"
    }
  }
}
