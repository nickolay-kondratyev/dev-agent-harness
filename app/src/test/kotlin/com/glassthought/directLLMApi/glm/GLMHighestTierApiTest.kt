package com.glassthought.directLLMApi.glm

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectBudgetHighLLM
import com.glassthought.shepherd.core.supporting.directLLMApi.glm.GlmDirectLlmFactory
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the budget-high GLM API tier using MockWebServer to verify
 * HTTP request construction and response parsing without real network calls.
 *
 * Shared HTTP contract tests are in [glmApiHttpContractTests].
 */
class GLMHighestTierApiTest : AsgardDescribeSpec({

    val modelName = "glm-5"

    glmApiHttpContractTests(modelName) { outFactory, httpClient, model, maxTokens, endpoint, token ->
        GlmDirectLlmFactory.createBudgetHighLLM(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = model,
            maxTokens = maxTokens,
            apiEndpoint = endpoint,
            apiToken = token,
        )
    }

    describe("GIVEN a budget-high GLM API instance") {
        it("THEN it implements DirectBudgetHighLLM") {
            val api = GlmDirectLlmFactory.createBudgetHighLLM(
                outFactory = outFactory,
                httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                modelName = modelName,
                maxTokens = 4096,
                apiEndpoint = "http://localhost/unused",
                apiToken = "test-token-123",
            )

            api.shouldBeInstanceOf<DirectBudgetHighLLM>()
        }
    }
})
