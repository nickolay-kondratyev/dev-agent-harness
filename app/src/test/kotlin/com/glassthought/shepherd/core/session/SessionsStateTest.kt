package com.glassthought.shepherd.core.session

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.agent.sessionresolver.HandshakeGuid
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SessionsStateTest : AsgardDescribeSpec({

    describe("GIVEN empty SessionsState") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()

        describe("WHEN lookup(guid)") {
            val result = sessionsState.lookup(guid)

            it("THEN returns null") {
                result shouldBe null
            }
        }
    }

    describe("GIVEN SessionsState with a registered session") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry(partName = "partA")
        sessionsState.register(guid, entry)

        describe("WHEN lookup(guid)") {
            val result = sessionsState.lookup(guid)

            it("THEN returns the registered SessionEntry") {
                result shouldBe entry
            }
        }
    }

    describe("GIVEN SessionsState with a registered session AND same guid re-registered") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val originalEntry = createTestSessionEntry(partName = "partA")
        sessionsState.register(guid, originalEntry)

        describe("WHEN register(same guid, new entry)") {
            val newEntry = createTestSessionEntry(partName = "partA-updated")
            sessionsState.register(guid, newEntry)

            it("THEN lookup returns the new entry") {
                val result = sessionsState.lookup(guid)
                result shouldBe newEntry
            }

            it("THEN lookup does not return the original entry") {
                val result = sessionsState.lookup(guid)
                result shouldNotBe originalEntry
            }
        }
    }

    describe("GIVEN sessions for partA and partB") {
        val sessionsState = SessionsState()
        val guidA = HandshakeGuid.generate()
        val guidB = HandshakeGuid.generate()
        val entryA = createTestSessionEntry(partName = "partA")
        val entryB = createTestSessionEntry(partName = "partB")
        sessionsState.register(guidA, entryA)
        sessionsState.register(guidB, entryB)

        describe("WHEN removeAllForPart(\"partA\")") {
            sessionsState.removeAllForPart("partA")

            it("THEN partA session is removed") {
                sessionsState.lookup(guidA) shouldBe null
            }

            it("THEN partB session is still present") {
                sessionsState.lookup(guidB) shouldBe entryB
            }
        }
    }

    describe("GIVEN sessions for partA") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry(partName = "partA")
        sessionsState.register(guid, entry)

        describe("WHEN removeAllForPart(\"partA\")") {
            val removed = sessionsState.removeAllForPart("partA")

            it("THEN returns the removed entries") {
                removed shouldContainExactlyInAnyOrder listOf(entry)
            }
        }
    }

    describe("GIVEN no sessions for partX") {
        val sessionsState = SessionsState()
        val guid = HandshakeGuid.generate()
        val entry = createTestSessionEntry(partName = "partA")
        sessionsState.register(guid, entry)

        describe("WHEN removeAllForPart(\"partX\")") {
            val removed = sessionsState.removeAllForPart("partX")

            it("THEN returns empty list") {
                removed.shouldBeEmpty()
            }
        }
    }

    describe("GIVEN multiple sessions for same part") {
        val sessionsState = SessionsState()
        val guid1 = HandshakeGuid.generate()
        val guid2 = HandshakeGuid.generate()
        val guid3 = HandshakeGuid.generate()
        val entry1 = createTestSessionEntry(partName = "partA")
        val entry2 = createTestSessionEntry(partName = "partA")
        val entry3 = createTestSessionEntry(partName = "partB")
        sessionsState.register(guid1, entry1)
        sessionsState.register(guid2, entry2)
        sessionsState.register(guid3, entry3)

        describe("WHEN removeAllForPart(\"partA\")") {
            val removed = sessionsState.removeAllForPart("partA")

            it("THEN removes all sessions for that part") {
                removed shouldHaveSize 2
            }

            it("THEN returns both removed entries") {
                removed shouldContainExactlyInAnyOrder listOf(entry1, entry2)
            }

            it("THEN sessions for other parts remain") {
                sessionsState.lookup(guid3) shouldBe entry3
            }
        }
    }
})
