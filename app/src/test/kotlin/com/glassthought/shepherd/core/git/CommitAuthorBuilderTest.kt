package com.glassthought.shepherd.core.git

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.glassthought.shepherd.core.data.AgentType
import com.glassthought.shepherd.core.supporting.git.CommitAuthorBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class CommitAuthorBuilderTest : AsgardDescribeSpec({

    describe("GIVEN build") {

        describe("WHEN called with AgentType.CLAUDE_CODE") {

            describe("AND model='sonnet', hostUsername='nickolaykondratyev'") {
                val result = CommitAuthorBuilder.build(
                    agentType = AgentType.CLAUDE_CODE,
                    model = "sonnet",
                    hostUsername = "nickolaykondratyev",
                )

                it("THEN returns 'CC_sonnet_WITH-nickolaykondratyev'") {
                    result shouldBe "CC_sonnet_WITH-nickolaykondratyev"
                }
            }

            describe("AND model='opus', hostUsername='nickolaykondratyev'") {
                val result = CommitAuthorBuilder.build(
                    agentType = AgentType.CLAUDE_CODE,
                    model = "opus",
                    hostUsername = "nickolaykondratyev",
                )

                it("THEN returns 'CC_opus_WITH-nickolaykondratyev'") {
                    result shouldBe "CC_opus_WITH-nickolaykondratyev"
                }
            }

            describe("AND model='glm-5', hostUsername='johndoe'") {
                val result = CommitAuthorBuilder.build(
                    agentType = AgentType.CLAUDE_CODE,
                    model = "glm-5",
                    hostUsername = "johndoe",
                )

                it("THEN returns 'CC_glm-5_WITH-johndoe'") {
                    result shouldBe "CC_glm-5_WITH-johndoe"
                }
            }
        }

        describe("WHEN called with AgentType.PI") {

            describe("AND model='sonnet', hostUsername='nickolaykondratyev'") {
                val result = CommitAuthorBuilder.build(
                    agentType = AgentType.PI,
                    model = "sonnet",
                    hostUsername = "nickolaykondratyev",
                )

                it("THEN returns 'PI_sonnet_WITH-nickolaykondratyev'") {
                    result shouldBe "PI_sonnet_WITH-nickolaykondratyev"
                }
            }
        }

        describe("WHEN called with invalid inputs") {

            it("THEN throws IllegalArgumentException for blank model") {
                shouldThrow<IllegalArgumentException> {
                    CommitAuthorBuilder.build(
                        agentType = AgentType.CLAUDE_CODE,
                        model = "  ",
                        hostUsername = "user",
                    )
                }
            }

            it("THEN throws IllegalArgumentException for blank hostUsername") {
                shouldThrow<IllegalArgumentException> {
                    CommitAuthorBuilder.build(
                        agentType = AgentType.CLAUDE_CODE,
                        model = "sonnet",
                        hostUsername = "",
                    )
                }
            }
        }
    }
})
