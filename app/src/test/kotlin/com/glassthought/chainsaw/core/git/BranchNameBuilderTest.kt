package com.glassthought.chainsaw.core.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.ticket.TicketData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
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

        describe("WHEN called with a title exceeding 60 characters") {
            val longTitle = "a".repeat(80)

            it("THEN result length is at most 60") {
                val slug = BranchNameBuilder.slugify(longTitle)
                (slug.length <= 60) shouldBe true
            }
        }

        describe("WHEN called with a title that truncates to end with a hyphen") {
            // 59 'a' chars + hyphen at position 60 + more chars after
            val titleThatTruncatesToHyphen = "a".repeat(59) + "-bbb"

            it("THEN result does not end with a hyphen") {
                val slug = BranchNameBuilder.slugify(titleThatTruncatesToHyphen)
                slug shouldNotEndWith "-"
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

            it("THEN the slug portion is at most 60 characters") {
                val branchName = BranchNameBuilder.build(ticketData, tryNumber = 1)
                // Format: {id}__{slug}__try-{N}
                val slug = branchName
                    .removePrefix("nid_abc123__")
                    .removeSuffix("__try-1")
                (slug.length <= 60) shouldBe true
            }
        }
    }
})
