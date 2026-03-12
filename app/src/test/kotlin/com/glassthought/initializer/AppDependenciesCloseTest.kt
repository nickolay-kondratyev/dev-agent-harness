package com.glassthought.initializer

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.ticketShepherd.core.initializer.data.ShepherdContext
import com.glassthought.ticketShepherd.core.initializer.ContextInitializer
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/**
 * Verifies that [ShepherdContext] properly implements [com.asgard.core.lifecycle.AsgardCloseable]
 * and shuts down [OkHttpClient] resources on close.
 */
class AppDependenciesCloseTest : AsgardDescribeSpec({

    describe("GIVEN AppDependencies") {
        describe("WHEN close() is called") {
            it("THEN OkHttpClient dispatcher executor service is shut down") {
                val httpClient = OkHttpClient.Builder().build()
                val deps = runBlocking {
                    ContextInitializer.standard().initialize(
                        outFactory = outFactory,
                        httpClient = httpClient,
                    )
                }

                deps.close()

                httpClient.dispatcher.executorService.isShutdown shouldBe true
            }
        }
    }
})
