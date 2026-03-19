package com.glassthought.shepherd.feedback

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FeedbackResolutionParserTest : AsgardDescribeSpec({

    val parser = FeedbackResolutionParser()

    describe("GIVEN feedback file with '## Resolution: ADDRESSED'") {
        val content = """
            # Race condition in session manager

            **File(s):** `src/main/kotlin/SessionManager.kt`

            The session manager has a TOCTOU race condition.

            ---

            ## Resolution: ADDRESSED
            Fixed by adding mutex around session lookup.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is Found") {
                result.shouldBeInstanceOf<ParseResult.Found>()
            }

            it("THEN resolution is ADDRESSED") {
                result shouldBe ParseResult.Found(FeedbackResolution.ADDRESSED)
            }
        }
    }

    describe("GIVEN feedback file with '## Resolution: REJECTED'") {
        val content = """
            # Use CoroutineScope instead of GlobalScope

            **File(s):** `src/main/kotlin/Launcher.kt`

            GlobalScope should be replaced with structured concurrency.

            ---

            ## Resolution: REJECTED
            WHY-NOT: GlobalScope is intentional here — this is the top-level launcher.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is Found") {
                result.shouldBeInstanceOf<ParseResult.Found>()
            }

            it("THEN resolution is REJECTED") {
                result shouldBe ParseResult.Found(FeedbackResolution.REJECTED)
            }
        }
    }

    describe("GIVEN feedback file with '## Resolution: SKIPPED'") {
        val content = """
            # Consider renaming variable

            **File(s):** `src/main/kotlin/Utils.kt`

            Minor naming suggestion.

            ---

            ## Resolution: SKIPPED
            Reviewed — this is a minor style preference that does not affect correctness.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is Found") {
                result.shouldBeInstanceOf<ParseResult.Found>()
            }

            it("THEN resolution is SKIPPED") {
                result shouldBe ParseResult.Found(FeedbackResolution.SKIPPED)
            }
        }
    }

    describe("GIVEN feedback file without any '## Resolution:' line") {
        val content = """
            # Missing null check in Parser

            **File(s):** `src/main/kotlin/Parser.kt`

            The parser does not handle null input, which will cause NPE.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is MissingMarker") {
                result shouldBe ParseResult.MissingMarker
            }
        }
    }

    describe("GIVEN feedback file with '## Resolution: INVALID_VALUE'") {
        val content = """
            # Some feedback

            ---

            ## Resolution: INVALID_VALUE
            Some reasoning.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is InvalidMarker") {
                result.shouldBeInstanceOf<ParseResult.InvalidMarker>()
            }

            it("THEN rawValue is 'INVALID_VALUE'") {
                (result as ParseResult.InvalidMarker).rawValue shouldBe "INVALID_VALUE"
            }
        }
    }

    describe("GIVEN feedback file with lowercase '## Resolution: addressed' (case insensitivity)") {
        val content = """
            # Some feedback

            ---

            ## Resolution: addressed
            Fixed it.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is Found with ADDRESSED") {
                result shouldBe ParseResult.Found(FeedbackResolution.ADDRESSED)
            }
        }
    }

    describe("GIVEN feedback file with resolution marker embedded in longer content") {
        val content = """
            # Critical bug in authentication module

            **File(s):** `src/main/kotlin/AuthModule.kt`

            There is a serious authentication bypass vulnerability when the token
            expires during an active session. The middleware does not re-validate.

            Additional context: This was reported by the security team on 2026-03-15.

            ---

            ## Resolution: ADDRESSED
            Added token re-validation middleware. Also added integration test
            covering the expired-token-during-active-session scenario.

            Follow-up: Created ticket for rate-limiting enhancement.
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is Found") {
                result.shouldBeInstanceOf<ParseResult.Found>()
            }

            it("THEN resolution is ADDRESSED") {
                result shouldBe ParseResult.Found(FeedbackResolution.ADDRESSED)
            }
        }
    }

    describe("GIVEN feedback file with '## Resolution:' but empty keyword") {
        val content = """
            # Some feedback

            ---

            ## Resolution:
        """.trimIndent()

        describe("WHEN parse is called") {
            val result = parser.parse(content)

            it("THEN result is MissingMarker") {
                result shouldBe ParseResult.MissingMarker
            }
        }
    }
})
