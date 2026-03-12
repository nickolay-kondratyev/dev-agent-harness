package com.glassthought.directLLMApi.glm

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.supporting.directLLMApi.DirectQuickCheapLLM
import com.glassthought.shepherd.core.supporting.directLLMApi.glm.GLMQuickCheapApi
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [GLMQuickCheapApi] using MockWebServer to verify
 * HTTP request construction and response parsing without real network calls.
 *
 * Shared HTTP contract tests are in [glmApiHttpContractTests].
 */
class GLMQuickCheapApiTest : AsgardDescribeSpec({

    val modelName = "glm-4.7-flash"

    glmApiHttpContractTests(modelName) { outFactory, httpClient, model, maxTokens, endpoint, token ->
        GLMQuickCheapApi(
            outFactory = outFactory,
            httpClient = httpClient,
            modelName = model,
            maxTokens = maxTokens,
            apiEndpoint = endpoint,
            apiToken = token,
        )
    }

    describe("GIVEN a GLMQuickCheapApi instance") {
        it("THEN it implements DirectQuickCheapLLM") {
            val api = GLMQuickCheapApi(
                outFactory = outFactory,
                httpClient = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
                modelName = modelName,
                maxTokens = 4096,
                apiEndpoint = "http://localhost/unused",
                apiToken = "test-token-123",
            )

            api.shouldBeInstanceOf<DirectQuickCheapLLM>()
        }
    }
})
