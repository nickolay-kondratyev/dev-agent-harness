package com.glassthought.shepherd.core.compaction

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.context.ProtocolVocabulary
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path

class SelfCompactionInstructionBuilderTest : AsgardDescribeSpec({

    describe("GIVEN SelfCompactionInstructionBuilder") {
        val builder = SelfCompactionInstructionBuilder()

        describe("WHEN build is called with a PRIVATE.md path") {
            val privateMdPath = Path.of("/repo/.ai_out/my_branch/execution/backend/impl/private/PRIVATE.md")
            val result = builder.build(privateMdPath)

            it("THEN renders the correct PRIVATE.md absolute path") {
                result shouldContain privateMdPath.toString()
            }

            it("THEN wraps the PRIVATE.md path in backticks") {
                result shouldContain "`$privateMdPath`"
            }

            it("THEN contains the callback signal script name") {
                result shouldContain ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT
            }

            it("THEN contains the self-compacted signal name") {
                result shouldContain ProtocolVocabulary.Signal.SELF_COMPACTED
            }

            it("THEN contains the full callback command") {
                result shouldContain
                    "${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.SELF_COMPACTED}"
            }

            it("THEN wraps the callback command in backticks") {
                result shouldContain
                    "`${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.SELF_COMPACTED}`"
            }

            it("THEN contains guideline to preserve what we're doing and why") {
                result shouldContain "What we're doing and why"
            }

            it("THEN contains guideline to preserve current progress") {
                result shouldContain "At which point we are in the work"
            }

            it("THEN contains guideline to preserve challenges and solutions") {
                result shouldContain "All challenges we've had and how we've solved them"
            }

            it("THEN contains guideline to preserve key decisions") {
                result shouldContain "Key decisions made and why"
            }

            it("THEN contains guideline to preserve codebase patterns") {
                result shouldContain "Any patterns or discoveries about the codebase"
            }

            it("THEN instructs the agent to make the summary concise") {
                result shouldContain "**concise**"
            }
        }
    }
})
