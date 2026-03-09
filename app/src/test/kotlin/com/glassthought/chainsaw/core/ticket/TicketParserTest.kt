package com.glassthought.chainsaw.core.ticket

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path

class TicketParserTest : AsgardDescribeSpec({

    fun resourcePath(name: String): Path =
        Path.of(
            TicketParserTest::class.java
                .getResource("/com/glassthought/chainsaw/core/ticket/$name")!!
                .toURI()
        )

    describe("GIVEN a valid ticket file") {
        val parser = TicketParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN id is parsed correctly") {
                val ticket = parser.parse(resourcePath("valid-ticket.md"))
                ticket.id shouldBe "nid_test_valid_ticket_001"
            }

            it("THEN title is parsed correctly") {
                val ticket = parser.parse(resourcePath("valid-ticket.md"))
                ticket.title shouldBe "My Test Ticket"
            }

            it("THEN status is parsed correctly") {
                val ticket = parser.parse(resourcePath("valid-ticket.md"))
                ticket.status shouldBe "open"
            }

            it("THEN description contains the body text") {
                val ticket = parser.parse(resourcePath("valid-ticket.md"))
                ticket.description shouldContain "This is the ticket description body."
            }

            it("THEN description does NOT contain --- delimiters") {
                val ticket = parser.parse(resourcePath("valid-ticket.md"))
                ticket.description shouldNotContain "---"
            }
        }
    }

    describe("GIVEN a ticket file missing the id field") {
        val parser = TicketParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("missing-id.md"))
                }
            }
        }
    }

    describe("GIVEN a ticket file missing the title field") {
        val parser = TicketParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    parser.parse(resourcePath("missing-title.md"))
                }
            }
        }
    }

    describe("GIVEN a ticket file with extra frontmatter fields") {
        val parser = TicketParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN additionalFields contains 'type'") {
                val ticket = parser.parse(resourcePath("extra-fields-ticket.md"))
                ticket.additionalFields shouldContainKey "type"
            }

            it("THEN additionalFields contains 'priority'") {
                val ticket = parser.parse(resourcePath("extra-fields-ticket.md"))
                ticket.additionalFields shouldContainKey "priority"
            }

            it("THEN additionalFields contains 'assignee'") {
                val ticket = parser.parse(resourcePath("extra-fields-ticket.md"))
                ticket.additionalFields shouldContainKey "assignee"
            }

            it("THEN additionalFields does NOT contain 'id'") {
                val ticket = parser.parse(resourcePath("extra-fields-ticket.md"))
                ticket.additionalFields shouldNotContainKey "id"
            }

            it("THEN additionalFields does NOT contain 'title'") {
                val ticket = parser.parse(resourcePath("extra-fields-ticket.md"))
                ticket.additionalFields shouldNotContainKey "title"
            }

            it("THEN additionalFields does NOT contain 'status'") {
                val ticket = parser.parse(resourcePath("extra-fields-ticket.md"))
                ticket.additionalFields shouldNotContainKey "status"
            }
        }
    }

    describe("GIVEN a ticket file with no body after closing ---") {
        val parser = TicketParser.standard(outFactory)

        describe("WHEN parse is called") {
            it("THEN description is empty") {
                val ticket = parser.parse(resourcePath("empty-body-ticket.md"))
                ticket.description shouldBe ""
            }
        }
    }
})
