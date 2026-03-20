package com.glassthought.shepherd.core.agent.adapter

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory

class CallbackScriptsDirTest : AsgardDescribeSpec({

    describe("GIVEN CallbackScriptsDir.validated") {

        describe("AND a valid directory with executable callback_shepherd.signal.sh") {
            describe("WHEN validated is called") {
                it("THEN returns a CallbackScriptsDir with the correct path") {
                    withValidCallbackScriptsDir { dirPath ->
                        val result = CallbackScriptsDir.validated(dirPath)

                        result.path shouldBe dirPath
                    }
                }
            }
        }

        describe("AND the directory does not exist") {
            describe("WHEN validated is called") {
                it("THEN throws IllegalStateException") {
                    val exception = shouldThrow<IllegalStateException> {
                        CallbackScriptsDir.validated("/nonexistent/path/that/does/not/exist")
                    }

                    exception.message shouldContain "does not exist"
                }

                it("THEN exception message contains the path") {
                    val badPath = "/nonexistent/path/that/does/not/exist"
                    val exception = shouldThrow<IllegalStateException> {
                        CallbackScriptsDir.validated(badPath)
                    }

                    exception.message shouldContain badPath
                }
            }
        }

        describe("AND the directory exists but callback_shepherd.signal.sh is missing") {
            describe("WHEN validated is called") {
                it("THEN throws IllegalStateException") {
                    val tempDir = createTempDirectory("callback-test-empty-").toFile()
                    try {
                        val exception = shouldThrow<IllegalStateException> {
                            CallbackScriptsDir.validated(tempDir.absolutePath)
                        }

                        exception.message shouldContain "callback_shepherd.signal.sh"
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
            }
        }

        describe("AND the directory exists with callback_shepherd.signal.sh but script is not executable") {
            describe("WHEN validated is called") {
                it("THEN throws IllegalStateException about executable permission") {
                    val tempDir = createTempDirectory("callback-test-noexec-").toFile()
                    try {
                        val script = File(tempDir, "callback_shepherd.signal.sh")
                        script.writeText("#!/bin/bash\necho hello")
                        script.setExecutable(false)

                        val exception = shouldThrow<IllegalStateException> {
                            CallbackScriptsDir.validated(tempDir.absolutePath)
                        }

                        exception.message shouldContain "not executable"
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
            }
        }

        describe("AND a file path is provided instead of a directory") {
            describe("WHEN validated is called") {
                it("THEN throws IllegalStateException about not being a directory") {
                    val tempDir = createTempDirectory("callback-test-file-").toFile()
                    val regularFile = File(tempDir, "some-file.txt")
                    regularFile.writeText("not a directory")
                    try {
                        val exception = shouldThrow<IllegalStateException> {
                            CallbackScriptsDir.validated(regularFile.absolutePath)
                        }

                        exception.message shouldContain "does not exist or is not a directory"
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
            }
        }
    }

    describe("GIVEN CallbackScriptsDir.unvalidated") {
        describe("WHEN called with any path string") {
            it("THEN returns a CallbackScriptsDir without filesystem validation") {
                val result = CallbackScriptsDir.unvalidated("/nonexistent/fake/path")

                result.path shouldBe "/nonexistent/fake/path"
            }
        }
    }

    describe("GIVEN two CallbackScriptsDir instances with the same path") {
        it("THEN they are equal") {
            val a = CallbackScriptsDir.unvalidated("/some/path")
            val b = CallbackScriptsDir.unvalidated("/some/path")

            (a == b) shouldBe true
        }

        it("THEN they have the same hashCode") {
            val a = CallbackScriptsDir.unvalidated("/some/path")
            val b = CallbackScriptsDir.unvalidated("/some/path")

            a.hashCode() shouldBe b.hashCode()
        }
    }

    describe("GIVEN two CallbackScriptsDir instances with different paths") {
        it("THEN they are not equal") {
            val a = CallbackScriptsDir.unvalidated("/path/a")
            val b = CallbackScriptsDir.unvalidated("/path/b")

            (a == b) shouldBe false
        }
    }
})

/**
 * Creates a temporary directory with a valid executable callback_shepherd.signal.sh,
 * runs [block], then cleans up.
 */
private fun withValidCallbackScriptsDir(block: (String) -> Unit) {
    val tempDir = createTempDirectory("callback-test-valid-").toFile()
    try {
        val script = File(tempDir, "callback_shepherd.signal.sh")
        script.writeText("#!/bin/bash\necho hello")
        script.setExecutable(true)
        block(tempDir.absolutePath)
    } finally {
        tempDir.deleteRecursively()
    }
}
