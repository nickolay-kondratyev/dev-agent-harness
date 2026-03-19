package com.glassthought.shepherd.core.question

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.readText

class QaAnswersFileWriterTest : AsgardDescribeSpec({

    val writer = QaAnswersFileWriterImpl()

    describe("GIVEN single QA pair") {
        val qaList = listOf(
            QuestionAndAnswer(
                question = "How should I handle the responsive layout for mobile devices?",
                answer = "Use CSS Grid with a mobile-first approach.",
            ),
        )

        describe("WHEN written to file") {
            val tempDir = Files.createTempDirectory("qa-writer-test")
            val filePath = writer.write(qaList, tempDir)
            val content = filePath.readText()

            it("THEN file is named qa_answers.md") {
                filePath.fileName.toString() shouldBe "qa_answers.md"
            }

            it("THEN content starts with QA Answers header") {
                content shouldContain "## QA Answers"
            }

            it("THEN content contains Question 1 heading") {
                content shouldContain "### Question 1"
            }

            it("THEN content contains question text in blockquote") {
                content shouldContain "> How should I handle the responsive layout for mobile devices?"
            }

            it("THEN content contains answer with bold prefix") {
                content shouldContain "**Answer:** Use CSS Grid with a mobile-first approach."
            }
        }
    }

    describe("GIVEN 3 QA pairs") {
        val qaList = listOf(
            QuestionAndAnswer(question = "Question A?", answer = "Answer A."),
            QuestionAndAnswer(question = "Question B?", answer = "Answer B."),
            QuestionAndAnswer(question = "Question C?", answer = "Answer C."),
        )

        describe("WHEN written to file") {
            val tempDir = Files.createTempDirectory("qa-writer-test")
            val filePath = writer.write(qaList, tempDir)
            val content = filePath.readText()

            it("THEN content contains Question 1") {
                content shouldContain "### Question 1"
            }

            it("THEN content contains Question 2") {
                content shouldContain "### Question 2"
            }

            it("THEN content contains Question 3") {
                content shouldContain "### Question 3"
            }

            it("THEN all questions are present") {
                content shouldContain "> Question A?"
                content shouldContain "> Question B?"
                content shouldContain "> Question C?"
            }

            it("THEN all answers are present") {
                content shouldContain "**Answer:** Answer A."
                content shouldContain "**Answer:** Answer B."
                content shouldContain "**Answer:** Answer C."
            }
        }
    }

    describe("GIVEN QA pair with empty question text") {
        val qaList = listOf(
            QuestionAndAnswer(question = "", answer = "Answer to empty question."),
        )

        describe("WHEN written to file") {
            val tempDir = Files.createTempDirectory("qa-writer-test")
            val filePath = writer.write(qaList, tempDir)
            val content = filePath.readText()

            it("THEN file is still written with correct structure") {
                content shouldContain "### Question 1"
            }

            it("THEN blockquote line contains empty quote marker") {
                content shouldContain "> "
            }

            it("THEN answer is present") {
                content shouldContain "**Answer:** Answer to empty question."
            }
        }
    }

    describe("GIVEN file already exists") {
        val qaListFirst = listOf(
            QuestionAndAnswer(question = "Old?", answer = "Old answer."),
        )
        val qaListSecond = listOf(
            QuestionAndAnswer(question = "New?", answer = "New answer."),
        )

        describe("WHEN written twice") {
            val tempDir = Files.createTempDirectory("qa-writer-test")
            writer.write(qaListFirst, tempDir)
            val filePath = writer.write(qaListSecond, tempDir)
            val content = filePath.readText()

            it("THEN file is overwritten with latest content") {
                content shouldContain "New?"
            }

            it("THEN old content is gone") {
                (content.contains("Old?")) shouldBe false
            }
        }
    }
})
