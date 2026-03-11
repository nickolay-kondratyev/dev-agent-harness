package com.glassthought.chainsaw.core.agent

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.chainsaw.core.agent.data.StartAgentRequest
import com.glassthought.chainsaw.core.data.AgentType
import com.glassthought.chainsaw.core.data.PhaseType
import io.kotest.matchers.shouldBe

class DefaultAgentTypeChooserTest : AsgardDescribeSpec({

    describe("GIVEN DefaultAgentTypeChooser") {
        val chooser = DefaultAgentTypeChooser()

        PhaseType.entries.forEach { phaseType ->
            describe("WHEN choose is called with phaseType=$phaseType") {
                val request = StartAgentRequest(
                    phaseType = phaseType,
                    workingDir = "/any/dir",
                )

                it("THEN returns CLAUDE_CODE") {
                    chooser.choose(request) shouldBe AgentType.CLAUDE_CODE
                }
            }
        }
    }
})
