package com.glassthought.shepherd.core.ticket

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.supporting.ticket.YamlFrontmatterParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class YamlFrontmatterParserTest : AsgardDescribeSpec({

    describe("GIVEN markdown content with valid YAML frontmatter") {
        val content = """
            ---
            id: test-id-001
            title: "Test Title"
            status: open
            ---

            This is the body text.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN yamlFields contains the id") {
                val result = YamlFrontmatterParser.parse(content)
                result.yamlFields["id"] shouldBe "test-id-001"
            }

            it("THEN yamlFields contains the title") {
                val result = YamlFrontmatterParser.parse(content)
                result.yamlFields["title"] shouldBe "Test Title"
            }

            it("THEN body contains the expected text") {
                val result = YamlFrontmatterParser.parse(content)
                result.body shouldContain "This is the body text."
            }

            it("THEN body does NOT contain the frontmatter delimiters") {
                val result = YamlFrontmatterParser.parse(content)
                result.body shouldNotContain "---"
            }
        }
    }

    describe("GIVEN markdown content without leading --- delimiter") {
        val content = """
            id: test-id-001
            title: "Test Title"
            ---

            Body here.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    YamlFrontmatterParser.parse(content)
                }
            }
        }
    }

    describe("GIVEN markdown content with opening --- but no closing ---") {
        val content = """
            ---
            id: test-id-001
            title: "Test Title"

            Body here with no closing delimiter.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN throws IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    YamlFrontmatterParser.parse(content)
                }
            }
        }
    }

    describe("GIVEN markdown content with extra frontmatter fields") {
        val content = """
            ---
            id: test-id-002
            title: "Extra Fields"
            status: open
            priority: 1
            assignee: engineer-one
            ---

            Body text.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN yamlFields contains 'priority' field") {
                YamlFrontmatterParser.parse(content).yamlFields shouldContainKey "priority"
            }

            it("THEN yamlFields contains 'assignee' field") {
                YamlFrontmatterParser.parse(content).yamlFields shouldContainKey "assignee"
            }
        }
    }

    describe("GIVEN markdown content where body has multiple paragraphs") {
        val content = """
            ---
            id: test-id-003
            title: "Multi Paragraph Body"
            ---

            First paragraph here.

            Second paragraph here.

            Third paragraph here.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN body contains first paragraph") {
                YamlFrontmatterParser.parse(content).body shouldContain "First paragraph here."
            }

            it("THEN body contains second paragraph") {
                YamlFrontmatterParser.parse(content).body shouldContain "Second paragraph here."
            }

            it("THEN body contains third paragraph") {
                YamlFrontmatterParser.parse(content).body shouldContain "Third paragraph here."
            }
        }
    }

    describe("GIVEN frontmatter with an unquoted ISO datetime value") {
        val content = """
            ---
            id: test-iso-date
            title: "ISO Date Test"
            created_iso: 2026-03-09T23:05:48Z
            ---

            Body.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN created_iso value is a String (not a java.util.Date)") {
                val result = YamlFrontmatterParser.parse(content)
                result.yamlFields["created_iso"]!!::class shouldBe String::class
            }

            it("THEN created_iso value equals the ISO string") {
                val result = YamlFrontmatterParser.parse(content)
                result.yamlFields["created_iso"] shouldBe "2026-03-09T23:05:48Z"
            }
        }
    }

    describe("GIVEN markdown content where body itself contains --- lines") {
        val content = """
            ---
            id: test-id-004
            title: "Body With Dashes"
            ---

            Body text before dashes.

            ---

            Body text after dashes.
        """.trimIndent()

        describe("WHEN parse is called") {
            it("THEN body contains text before the inner ---") {
                YamlFrontmatterParser.parse(content).body shouldContain "Body text before dashes."
            }

            it("THEN body contains text after the inner ---") {
                YamlFrontmatterParser.parse(content).body shouldContain "Body text after dashes."
            }

            it("THEN yamlFields contains id from frontmatter only") {
                val result = YamlFrontmatterParser.parse(content)
                result.yamlFields["id"] shouldBe "test-id-004"
            }
        }
    }
})
