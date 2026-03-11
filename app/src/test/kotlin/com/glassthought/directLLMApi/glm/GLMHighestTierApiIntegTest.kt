package com.glassthought.directLLMApi.glm

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.Constants
import com.glassthought.chainsaw.core.supporting.directLLMApi.ChatRequest
import com.glassthought.chainsaw.core.supporting.directLLMApi.glm.GLMHighestTierApi
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.string.shouldNotBeBlank
import okhttp3.OkHttpClient
import org.example.isIntegTestEnabled
import java.util.concurrent.TimeUnit

/**
 * Integration test for [GLMHighestTierApi] against the real Z.AI API.
 *
 * Requires:
 * - `-PrunIntegTests=true` Gradle property
 * - `Z_AI_GLM_API_TOKEN` environment variable set with a valid API token
 */
@OptIn(ExperimentalKotest::class)
class GLMHighestTierApiIntegTest : AsgardDescribeSpec({

    describe("GIVEN GLMHighestTierApi with real API").config(isIntegTestEnabled()) {
        val apiToken = System.getenv(Constants.Z_AI_API.API_TOKEN_ENV_VAR)
            ?: throw IllegalStateException(
                "Integration test requires [${Constants.Z_AI_API.API_TOKEN_ENV_VAR}] environment variable to be set"
            )

        val config = Constants.getConfigurationObject()

        val httpClient = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val api = GLMHighestTierApi(
          outFactory = outFactory,
          httpClient = httpClient,
          modelName = config.zAiGlmConfig.modelName,
          maxTokens = config.zAiGlmConfig.maxTokens,
          apiEndpoint = Constants.Z_AI_API.CHAT_COMPLETIONS_ENDPOINT,
          apiToken = apiToken,
        )

        describe("WHEN calling with a simple math question") {
            it("THEN response is non-empty") {
                val response = api.call(ChatRequest("What is 2+2? Reply with just the number."))

                response.text.shouldNotBeBlank()
            }
        }
    }
})
