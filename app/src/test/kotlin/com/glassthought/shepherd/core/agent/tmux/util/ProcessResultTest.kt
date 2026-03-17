package com.glassthought.shepherd.core.agent.tmux.util

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class ProcessResultTest : AsgardDescribeSpec({

    describe("GIVEN ProcessResult.orThrow") {

        describe("WHEN exitCode is 0") {
            it("THEN no exception is thrown") {
                val result = ProcessResult(exitCode = 0, stdOut = "", stdErr = "")
                shouldNotThrowAny { result.orThrow("do something") }
            }
        }

        describe("WHEN exitCode is non-zero") {
            it("THEN IllegalStateException is thrown with expected message") {
                val result = ProcessResult(exitCode = 1, stdOut = "", stdErr = "some error")
                val ex = shouldThrow<IllegalStateException> {
                    result.orThrow("do something")
                }
                ex.message shouldBe "Failed to do something. Exit code: [1]. Stderr: [some error]"
            }
        }
    }
})
