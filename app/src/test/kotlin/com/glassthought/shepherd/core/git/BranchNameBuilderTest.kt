package com.glassthought.shepherd.core.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.supporting.git.BranchNameBuilder
import com.glassthought.shepherd.core.supporting.ticket.TicketData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotEndWith
import io.kotest.matchers.string.shouldStartWith

class BranchNameBuilderTest : AsgardDescribeSpec({

    // -- slugify tests --

    describe("GIVEN slugify") {

        describe("WHEN called with a simple title 'My Feature'") {
            it("THEN returns 'my-feature'") {
                BranchNameBuilder.slugify("My Feature") shouldBe "my-feature"
            }
        }

        describe("WHEN called with special characters 'Fix: bug #123!'") {
            it("THEN returns 'fix-bug-123'") {
                BranchNameBuilder.slugify("Fix: bug #123!") shouldBe "fix-bug-123"
            }
        }

        describe("WHEN called with consecutive spaces 'fix   the    bug'") {
            it("THEN returns 'fix-the-bug'") {
                BranchNameBuilder.slugify("fix   the    bug") shouldBe "fix-the-bug"
            }
        }

        describe("WHEN called with leading/trailing special chars '---hello---'") {
            it("THEN returns 'hello'") {
                BranchNameBuilder.slugify("---hello---") shouldBe "hello"
            }
        }

        describe("WHEN called with an empty string") {
            it("THEN returns 'untitled'") {
                BranchNameBuilder.slugify("") shouldBe "untitled"
            }
        }

        describe("WHEN called with only special characters '!@#\$%^'") {
            it("THEN returns 'untitled'") {
                BranchNameBuilder.slugify("!@#\$%^") shouldBe "untitled"
            }
        }

        describe("WHEN called with unicode characters 'caf\u00e9 latt\u00e9'") {
            it("THEN non-ascii chars become hyphens producing 'caf-latt'") {
                val slug = BranchNameBuilder.slugify("caf\u00e9 latt\u00e9")
                slug shouldBe "caf-latt"
            }
        }

        // -- No-truncation boundary tests --

        describe("WHEN called with a title that slugifies to exactly 50 chars") {
            // 50 'a' chars = exactly MAX_SLUG_LENGTH
            val exactTitle = "a".repeat(50)

            it("THEN returns the full slug without hash suffix") {
                BranchNameBuilder.slugify(exactTitle) shouldBe "a".repeat(50)
            }
        }

        // -- Truncation tests --

        describe("WHEN called with a title that slugifies to exactly 51 chars") {
            // 51 'a' chars = one over the limit, single-word character-level fallback
            val overByOneTitle = "a".repeat(51)

            it("THEN result is exactly 50 chars") {
                BranchNameBuilder.slugify(overByOneTitle).length shouldBe 50
            }

            it("THEN result starts with 43 'a' chars") {
                BranchNameBuilder.slugify(overByOneTitle) shouldStartWith "a".repeat(43)
            }

            it("THEN result ends with a 6-char hex hash suffix") {
                BranchNameBuilder.slugify(overByOneTitle) shouldMatch Regex(".*-[a-f0-9]{6}$")
            }

            it("THEN result matches expected value") {
                BranchNameBuilder.slugify(overByOneTitle) shouldBe "a".repeat(43) + "-aca32b"
            }
        }

        describe("WHEN called with a single long word of 80 'a' chars") {
            val longTitle = "a".repeat(80)

            it("THEN result is exactly 50 chars") {
                BranchNameBuilder.slugify(longTitle).length shouldBe 50
            }

            it("THEN result uses character-level fallback with hash") {
                BranchNameBuilder.slugify(longTitle) shouldBe "a".repeat(43) + "-86f336"
            }
        }

        describe("WHEN called with first word exactly 43 chars followed by more words") {
            // First word fits exactly in MAX_WORD_BUDGET, but total exceeds MAX_SLUG_LENGTH
            val title = "a".repeat(43) + "-more-words"

            it("THEN result is exactly 50 chars") {
                BranchNameBuilder.slugify(title).length shouldBe 50
            }

            it("THEN result keeps the 43-char first word and appends hash") {
                BranchNameBuilder.slugify(title) shouldBe "a".repeat(43) + "-d56f93"
            }
        }

        describe("WHEN called with a long multi-word title that exceeds 50 chars") {
            // 49 'a' chars + '-bbb' = 53 chars after slugify; first word is 49 chars > 43
            val titleThatExceeds = "a".repeat(49) + "-bbb"

            it("THEN result does not end with a hyphen") {
                BranchNameBuilder.slugify(titleThatExceeds) shouldNotEndWith "-"
            }

            it("THEN result is at most 50 chars") {
                (BranchNameBuilder.slugify(titleThatExceeds).length <= 50) shouldBe true
            }

            it("THEN result uses character-level fallback since first word exceeds 43 chars") {
                BranchNameBuilder.slugify(titleThatExceeds) shouldBe "a".repeat(43) + "-104124"
            }
        }

        describe("WHEN called with the spec canonical example") {
            val title = "implement user authentication flow with oauth and session"

            it("THEN returns word-boundary truncated slug with hash suffix") {
                BranchNameBuilder.slugify(title) shouldBe
                    "implement-user-authentication-flow-with-c33b35"
            }

            it("THEN result is 46 chars") {
                BranchNameBuilder.slugify(title).length shouldBe 46
            }
        }

        describe("WHEN called with a single long word of 60 chars") {
            val longWord = "a".repeat(60)

            it("THEN result starts with 43 chars of the word") {
                BranchNameBuilder.slugify(longWord) shouldStartWith "a".repeat(43)
            }

            it("THEN result ends with hash suffix") {
                BranchNameBuilder.slugify(longWord) shouldMatch Regex(".*-[a-f0-9]{6}$")
            }

            it("THEN result is exactly 50 chars") {
                BranchNameBuilder.slugify(longWord).length shouldBe 50
            }
        }

        // -- Determinism --

        describe("WHEN called twice with the same long input") {
            val longTitle = "implement user authentication flow with oauth and session management"

            it("THEN returns identical results") {
                val first = BranchNameBuilder.slugify(longTitle)
                val second = BranchNameBuilder.slugify(longTitle)
                first shouldBe second
            }
        }

        // -- Uniqueness --

        describe("WHEN called with two similar long titles sharing a prefix") {
            val titleA = "implement user authentication flow with oauth and session management"
            val titleB = "implement user authentication flow with oauth and token refresh"

            it("THEN produces different slugs") {
                val slugA = BranchNameBuilder.slugify(titleA)
                val slugB = BranchNameBuilder.slugify(titleB)
                slugA shouldNotBe slugB
            }

            it("THEN slugA has expected hash suffix") {
                BranchNameBuilder.slugify(titleA) shouldBe
                    "implement-user-authentication-flow-with-8461c2"
            }

            it("THEN slugB has expected hash suffix") {
                BranchNameBuilder.slugify(titleB) shouldBe
                    "implement-user-authentication-flow-with-5187b8"
            }
        }

        // -- Invariants for truncated slugs --

        describe("WHEN called with any truncated slug") {
            val longTitle = "this is a very long title that should " +
                "definitely exceed the fifty character maximum slug length"

            it("THEN result never ends with a hyphen") {
                BranchNameBuilder.slugify(longTitle) shouldNotEndWith "-"
            }

            it("THEN result is at most 50 chars") {
                (BranchNameBuilder.slugify(longTitle).length <= 50) shouldBe true
            }
        }
    }

    // -- build tests --

    describe("GIVEN build") {

        describe("WHEN called with id='TK-001', title='My Feature', tryNumber=1") {
            val ticketData = TicketData(
                id = "TK-001",
                title = "My Feature",
                status = null,
                description = "",
            )

            it("THEN returns 'TK-001__my-feature__try-1'") {
                BranchNameBuilder.build(ticketData, tryNumber = 1) shouldBe "TK-001__my-feature__try-1"
            }
        }

        describe("WHEN called with id='TK-001', title='My Feature', tryNumber=3") {
            val ticketData = TicketData(
                id = "TK-001",
                title = "My Feature",
                status = null,
                description = "",
            )

            it("THEN returns 'TK-001__my-feature__try-3'") {
                BranchNameBuilder.build(ticketData, tryNumber = 3) shouldBe "TK-001__my-feature__try-3"
            }
        }

        describe("WHEN called with tryNumber=0") {
            val ticketData = TicketData(
                id = "TK-001",
                title = "My Feature",
                status = null,
                description = "",
            )

            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    BranchNameBuilder.build(ticketData, tryNumber = 0)
                }
            }
        }

        describe("WHEN called with tryNumber=-1") {
            val ticketData = TicketData(
                id = "TK-001",
                title = "My Feature",
                status = null,
                description = "",
            )

            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    BranchNameBuilder.build(ticketData, tryNumber = -1)
                }
            }
        }

        describe("WHEN called with blank id") {
            val ticketData = TicketData(
                id = "  ",
                title = "My Feature",
                status = null,
                description = "",
            )

            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    BranchNameBuilder.build(ticketData, tryNumber = 1)
                }
            }
        }

        describe("WHEN called with a long title and id='nid_abc123'") {
            val longTitle = "This is a very long title that exceeds the sixty character slug limit by quite a bit"
            val ticketData = TicketData(
                id = "nid_abc123",
                title = longTitle,
                status = null,
                description = "",
            )

            it("THEN branch name starts with 'nid_abc123__'") {
                val branchName = BranchNameBuilder.build(ticketData, tryNumber = 1)
                branchName shouldStartWith "nid_abc123__"
            }

            it("THEN branch name ends with '__try-1'") {
                val branchName = BranchNameBuilder.build(ticketData, tryNumber = 1)
                branchName shouldEndWith "__try-1"
            }

            it("THEN the slug portion is at most 50 characters") {
                val branchName = BranchNameBuilder.build(ticketData, tryNumber = 1)
                val slug = branchName
                    .removePrefix("nid_abc123__")
                    .removeSuffix("__try-1")
                (slug.length <= 50) shouldBe true
            }

            it("THEN the slug portion contains a hash suffix") {
                val branchName = BranchNameBuilder.build(ticketData, tryNumber = 1)
                val slug = branchName
                    .removePrefix("nid_abc123__")
                    .removeSuffix("__try-1")
                slug shouldMatch Regex(".*-[a-f0-9]{6}$")
            }
        }
    }
})
