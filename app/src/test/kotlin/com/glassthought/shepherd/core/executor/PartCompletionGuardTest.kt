package com.glassthought.shepherd.core.executor

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

class PartCompletionGuardTest : AsgardDescribeSpec({

    val guard = PartCompletionGuard()

    fun createTempFeedbackDirs(): Pair<Path, Path> {
        val base = Files.createTempDirectory("guard-test")
        val pending = base.resolve("pending")
        val addressed = base.resolve("addressed")
        Files.createDirectories(pending)
        Files.createDirectories(addressed)
        return pending to addressed
    }

    // ── PASS with empty pending → Completed ──────────────────────────────

    describe("GIVEN an empty pending directory") {
        describe("WHEN the guard validates") {

            it("THEN the result is Passed") {
                val (pending, addressed) = createTempFeedbackDirs()

                val result = guard.validate(pending, addressed)
                result shouldBe PartCompletionGuard.GuardResult.Passed
            }
        }
    }

    // ── PASS with non-existent pending dir → Completed ───────────────────

    describe("GIVEN a non-existent pending directory") {
        describe("WHEN the guard validates") {

            it("THEN the result is Passed") {
                val nonExistent = Path.of("/tmp/non-existent-guard-test-${System.nanoTime()}/pending")
                val addressed = Path.of("/tmp/non-existent-guard-test-${System.nanoTime()}/addressed")

                val result = guard.validate(nonExistent, addressed)
                result shouldBe PartCompletionGuard.GuardResult.Passed
            }
        }
    }

    // ── PASS with pending critical → AgentCrashed ────────────────────────

    describe("GIVEN a pending directory with a critical feedback file") {
        describe("WHEN the guard validates") {

            it("THEN the result is Failed") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("critical__missing-null-check.md"), "# Missing null check")

                val result = guard.validate(pending, addressed)
                result.shouldBeInstanceOf<PartCompletionGuard.GuardResult.Failed>()
            }

            it("THEN the failure message mentions the file name") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("critical__missing-null-check.md"), "# Missing null check")

                val result = guard.validate(pending, addressed) as PartCompletionGuard.GuardResult.Failed
                result.message shouldBe "Reviewer signaled pass with unaddressed critical/important " +
                    "feedback items in pending/: critical__missing-null-check.md"
            }
        }
    }

    // ── PASS with pending important → AgentCrashed ───────────────────────

    describe("GIVEN a pending directory with an important feedback file") {
        describe("WHEN the guard validates") {

            it("THEN the result is Failed") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("important__error-handling.md"), "# Error handling needed")

                val result = guard.validate(pending, addressed)
                result.shouldBeInstanceOf<PartCompletionGuard.GuardResult.Failed>()
            }

            it("THEN the failure message mentions the file name") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("important__error-handling.md"), "# Error handling needed")

                val result = guard.validate(pending, addressed) as PartCompletionGuard.GuardResult.Failed
                result.message shouldBe "Reviewer signaled pass with unaddressed critical/important " +
                    "feedback items in pending/: important__error-handling.md"
            }
        }
    }

    // ── PASS with only optional in pending → Completed (files moved) ─────

    describe("GIVEN a pending directory with only optional feedback files") {
        describe("WHEN the guard validates") {

            it("THEN the result is Passed") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__naming-suggestion.md"), "# Naming suggestion")

                val result = guard.validate(pending, addressed)
                result shouldBe PartCompletionGuard.GuardResult.Passed
            }

            it("THEN the optional file is moved to addressed directory") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__naming-suggestion.md"), "# Naming suggestion")

                guard.validate(pending, addressed)

                Files.exists(pending.resolve("optional__naming-suggestion.md")) shouldBe false
                Files.exists(addressed.resolve("optional__naming-suggestion.md")) shouldBe true
            }

            it("THEN the moved file retains its content") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__naming-suggestion.md"), "# Naming suggestion")

                guard.validate(pending, addressed)

                Files.readString(addressed.resolve("optional__naming-suggestion.md")) shouldBe "# Naming suggestion"
            }
        }
    }

    // ── PASS with mix of optional + critical → Failed ────────────────────

    describe("GIVEN a pending directory with both optional and critical files") {
        describe("WHEN the guard validates") {

            it("THEN the result is Failed (critical blocks completion)") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__style-tweak.md"), "# Style tweak")
                Files.writeString(pending.resolve("critical__security-issue.md"), "# Security issue")

                val result = guard.validate(pending, addressed)
                result.shouldBeInstanceOf<PartCompletionGuard.GuardResult.Failed>()
            }

            it("THEN optional files are NOT moved (guard fails before moving)") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__style-tweak.md"), "# Style tweak")
                Files.writeString(pending.resolve("critical__security-issue.md"), "# Security issue")

                guard.validate(pending, addressed)

                Files.exists(pending.resolve("optional__style-tweak.md")) shouldBe true
                Files.exists(addressed.resolve("optional__style-tweak.md")) shouldBe false
            }
        }
    }

    // ── PASS with mix of optional + important → Failed ───────────────────

    describe("GIVEN a pending directory with both optional and important files") {
        describe("WHEN the guard validates") {

            it("THEN the result is Failed (important blocks completion)") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__naming.md"), "# Naming")
                Files.writeString(pending.resolve("important__missing-test.md"), "# Missing test")

                val result = guard.validate(pending, addressed)
                result.shouldBeInstanceOf<PartCompletionGuard.GuardResult.Failed>()
            }
        }
    }

    // ── PASS with multiple optional files → all moved ────────────────────

    describe("GIVEN a pending directory with multiple optional files and no blocking files") {
        describe("WHEN the guard validates") {

            it("THEN all optional files are moved to addressed") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("optional__naming.md"), "# Naming")
                Files.writeString(pending.resolve("optional__formatting.md"), "# Formatting")

                guard.validate(pending, addressed)

                Files.exists(addressed.resolve("optional__naming.md")) shouldBe true
                Files.exists(addressed.resolve("optional__formatting.md")) shouldBe true
                Files.exists(pending.resolve("optional__naming.md")) shouldBe false
                Files.exists(pending.resolve("optional__formatting.md")) shouldBe false
            }
        }
    }

    // ── PASS with multiple blocking files → all listed in message ────────

    describe("GIVEN a pending directory with multiple critical and important files") {
        describe("WHEN the guard validates") {

            it("THEN the failure message lists all blocking files") {
                val (pending, addressed) = createTempFeedbackDirs()
                Files.writeString(pending.resolve("critical__bug-a.md"), "# Bug A")
                Files.writeString(pending.resolve("important__perf-issue.md"), "# Perf issue")

                val result = guard.validate(pending, addressed) as PartCompletionGuard.GuardResult.Failed
                // Both files should be mentioned (order may vary due to filesystem)
                result.message.contains("critical__bug-a.md") shouldBe true
                result.message.contains("important__perf-issue.md") shouldBe true
            }
        }
    }
})
