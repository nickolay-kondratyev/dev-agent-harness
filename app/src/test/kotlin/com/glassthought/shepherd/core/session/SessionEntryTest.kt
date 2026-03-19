package com.glassthought.shepherd.core.session

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.question.UserQuestionContext
import com.glassthought.shepherd.core.state.SubPartRole
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentLinkedQueue

class SessionEntryTest : AsgardDescribeSpec({

    describe("GIVEN SessionEntry with empty questionQueue") {
        val entry = createTestSessionEntry(subPartIndex = 0)

        it("THEN isQAPending is false") {
            entry.isQAPending shouldBe false
        }
    }

    describe("GIVEN SessionEntry with non-empty questionQueue") {
        val queue = ConcurrentLinkedQueue<UserQuestionContext>()
        queue.add(createTestUserQuestionContext())
        val entry = createTestSessionEntry(subPartIndex = 0, questionQueue = queue)

        it("THEN isQAPending is true") {
            entry.isQAPending shouldBe true
        }
    }

    describe("GIVEN SessionEntry with subPartIndex 0") {
        val entry = createTestSessionEntry(subPartIndex = 0)

        it("THEN role is DOER") {
            entry.role shouldBe SubPartRole.DOER
        }
    }

    describe("GIVEN SessionEntry with subPartIndex 1") {
        val entry = createTestSessionEntry(subPartIndex = 1)

        it("THEN role is REVIEWER") {
            entry.role shouldBe SubPartRole.REVIEWER
        }
    }

    describe("GIVEN SessionEntry with empty queue") {
        val entry = createTestSessionEntry(subPartIndex = 0)

        describe("WHEN question added to queue") {
            entry.questionQueue.add(createTestUserQuestionContext())

            it("THEN isQAPending becomes true") {
                entry.isQAPending shouldBe true
            }
        }
    }

    describe("GIVEN SessionEntry with questions in queue") {
        val queue = ConcurrentLinkedQueue<UserQuestionContext>()
        queue.add(createTestUserQuestionContext())
        val entry = createTestSessionEntry(subPartIndex = 0, questionQueue = queue)

        describe("WHEN queue is drained") {
            entry.questionQueue.clear()

            it("THEN isQAPending becomes false") {
                entry.isQAPending shouldBe false
            }
        }
    }
})
