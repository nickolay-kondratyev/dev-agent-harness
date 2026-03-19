package com.glassthought.shepherd.core.state

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldHaveSize

class PlanCurrentStateModelTest : AsgardDescribeSpec({

    val mapper = ShepherdObjectMapper.create()

    // ── Phase enum serialization ──

    describe("GIVEN Phase enum") {

        describe("WHEN serializing PLANNING") {
            val json = mapper.writeValueAsString(Phase.PLANNING)

            it("THEN serializes to lowercase 'planning'") {
                json shouldBe "\"planning\""
            }
        }

        describe("WHEN serializing EXECUTION") {
            val json = mapper.writeValueAsString(Phase.EXECUTION)

            it("THEN serializes to lowercase 'execution'") {
                json shouldBe "\"execution\""
            }
        }

        describe("WHEN deserializing 'planning'") {
            val phase = mapper.readValue<Phase>("\"planning\"")

            it("THEN returns PLANNING") {
                phase shouldBe Phase.PLANNING
            }
        }

        describe("WHEN deserializing 'execution'") {
            val phase = mapper.readValue<Phase>("\"execution\"")

            it("THEN returns EXECUTION") {
                phase shouldBe Phase.EXECUTION
            }
        }
    }

    // ── IterationConfig round-trip ──

    describe("GIVEN IterationConfig") {

        describe("WHEN serializing with default current=0") {
            val config = IterationConfig(max = 3)
            val json = mapper.writeValueAsString(config)
            val deserialized = mapper.readValue<IterationConfig>(json)

            it("THEN round-trips correctly") {
                deserialized shouldBe config
            }
        }

        describe("WHEN deserializing with only max field") {
            val json = """{"max": 3}"""
            val config = mapper.readValue<IterationConfig>(json)

            it("THEN current defaults to 0") {
                config.current shouldBe 0
            }

            it("THEN max is set correctly") {
                config.max shouldBe 3
            }
        }

        describe("WHEN serializing with current=1") {
            val config = IterationConfig(max = 3, current = 1)
            val json = mapper.writeValueAsString(config)
            val deserialized = mapper.readValue<IterationConfig>(json)

            it("THEN round-trips correctly") {
                deserialized shouldBe config
            }
        }
    }

    // ── AgentSessionInfo round-trip ──

    describe("GIVEN AgentSessionInfo") {

        describe("WHEN round-tripping") {
            val info = AgentSessionInfo(id = "77d5b7ea-cf04-453b-8867-162404763e18")
            val json = mapper.writeValueAsString(info)
            val deserialized = mapper.readValue<AgentSessionInfo>(json)

            it("THEN preserves id") {
                deserialized shouldBe info
            }
        }
    }

    // ── SessionRecord round-trip ──

    describe("GIVEN SessionRecord") {

        describe("WHEN round-tripping") {
            val record = SessionRecord(
                handshakeGuid = "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                agentSession = AgentSessionInfo(id = "77d5b7ea-cf04-453b-8867-162404763e18"),
                agentType = "ClaudeCode",
                model = "sonnet",
                timestamp = "2026-03-10T15:30:00Z",
            )
            val json = mapper.writeValueAsString(record)
            val deserialized = mapper.readValue<SessionRecord>(json)

            it("THEN preserves all fields") {
                deserialized shouldBe record
            }
        }
    }

    // ── Plan flow JSON fixture (no runtime fields) ──

    describe("GIVEN plan_flow.json fixture (no runtime fields)") {

        val planFlowJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design the dashboard UI",
              "subParts": [
                { "name": "impl", "role": "UI_DESIGNER", "agentType": "ClaudeCode", "model": "sonnet" },
                { "name": "review", "role": "UI_REVIEWER", "agentType": "ClaudeCode", "model": "sonnet",
                  "iteration": { "max": 3 } }
              ]
            }
          ]
        }
        """.trimIndent()

        val state = mapper.readValue<CurrentState>(planFlowJson)

        describe("WHEN deserializing") {

            it("THEN has one part") {
                state.parts shouldHaveSize 1
            }

            it("THEN part name is ui_design") {
                state.parts[0].name shouldBe "ui_design"
            }

            it("THEN part phase is EXECUTION") {
                state.parts[0].phase shouldBe Phase.EXECUTION
            }

            it("THEN part has two subParts") {
                state.parts[0].subParts shouldHaveSize 2
            }
        }

        describe("WHEN inspecting impl subPart") {
            val impl = state.parts[0].subParts[0]

            it("THEN status is null") {
                impl.status.shouldBeNull()
            }

            it("THEN iteration is null") {
                impl.iteration.shouldBeNull()
            }

            it("THEN sessionIds is null") {
                impl.sessionIds.shouldBeNull()
            }

            it("THEN role is UI_DESIGNER") {
                impl.role shouldBe "UI_DESIGNER"
            }
        }

        describe("WHEN inspecting review subPart") {
            val review = state.parts[0].subParts[1]

            it("THEN status is null") {
                review.status.shouldBeNull()
            }

            it("THEN iteration max is 3") {
                review.iteration.shouldNotBeNull()
                review.iteration!!.max shouldBe 3
            }

            it("THEN iteration current defaults to 0") {
                review.iteration!!.current shouldBe 0
            }

            it("THEN sessionIds is null") {
                review.sessionIds.shouldBeNull()
            }
        }

        describe("WHEN re-serializing plan flow") {
            val reserialized = mapper.writeValueAsString(state)
            val roundTripped = mapper.readValue<CurrentState>(reserialized)

            it("THEN round-trips correctly") {
                roundTripped shouldBe state
            }
        }
    }

    // ── Current state JSON fixture (with runtime fields) ──

    describe("GIVEN current_state.json fixture (with runtime fields)") {

        val currentStateJson = """
        {
          "parts": [
            {
              "name": "ui_design",
              "phase": "execution",
              "description": "Design the dashboard UI",
              "subParts": [
                {
                  "name": "impl",
                  "role": "UI_DESIGNER",
                  "agentType": "ClaudeCode",
                  "model": "sonnet",
                  "status": "IN_PROGRESS",
                  "sessionIds": [
                    {
                      "handshakeGuid": "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                      "agentSession": { "id": "77d5b7ea-cf04-453b-8867-162404763e18" },
                      "agentType": "ClaudeCode",
                      "model": "sonnet",
                      "timestamp": "2026-03-10T15:30:00Z"
                    }
                  ]
                },
                {
                  "name": "review",
                  "role": "UI_REVIEWER",
                  "agentType": "ClaudeCode",
                  "model": "sonnet",
                  "status": "IN_PROGRESS",
                  "iteration": { "max": 3, "current": 1 },
                  "sessionIds": [
                    {
                      "handshakeGuid": "handshake.b2c3d4e5-f6a7-8901-bcde-f12345678901",
                      "agentSession": { "id": "88e6c8fb-df15-564c-9978-273515874f29" },
                      "agentType": "ClaudeCode",
                      "model": "sonnet",
                      "timestamp": "2026-03-10T15:45:00Z"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val state = mapper.readValue<CurrentState>(currentStateJson)

        describe("WHEN inspecting impl subPart") {
            val impl = state.parts[0].subParts[0]

            it("THEN status is IN_PROGRESS") {
                impl.status shouldBe SubPartStatus.IN_PROGRESS
            }

            it("THEN has one session record") {
                impl.sessionIds.shouldNotBeNull()
                impl.sessionIds!! shouldHaveSize 1
            }

            it("THEN session handshakeGuid is correct") {
                impl.sessionIds!![0].handshakeGuid shouldBe "handshake.a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            it("THEN session agentSession id is correct") {
                impl.sessionIds!![0].agentSession.id shouldBe "77d5b7ea-cf04-453b-8867-162404763e18"
            }

            it("THEN session timestamp is correct") {
                impl.sessionIds!![0].timestamp shouldBe "2026-03-10T15:30:00Z"
            }

            it("THEN iteration is null") {
                impl.iteration.shouldBeNull()
            }
        }

        describe("WHEN inspecting review subPart") {
            val review = state.parts[0].subParts[1]

            it("THEN status is IN_PROGRESS") {
                review.status shouldBe SubPartStatus.IN_PROGRESS
            }

            it("THEN iteration max is 3") {
                review.iteration!!.max shouldBe 3
            }

            it("THEN iteration current is 1") {
                review.iteration!!.current shouldBe 1
            }

            it("THEN has one session record") {
                review.sessionIds!! shouldHaveSize 1
            }

            it("THEN session agentSession id is correct") {
                review.sessionIds!![0].agentSession.id shouldBe "88e6c8fb-df15-564c-9978-273515874f29"
            }
        }

        describe("WHEN re-serializing current state") {
            val reserialized = mapper.writeValueAsString(state)
            val roundTripped = mapper.readValue<CurrentState>(reserialized)

            it("THEN round-trips correctly") {
                roundTripped shouldBe state
            }
        }
    }

    // ── NON_NULL serialization inclusion ──

    describe("GIVEN SubPart with null optional fields") {
        val subPart = SubPart(name = "impl", role = "DOER", agentType = "ClaudeCode", model = "sonnet")
        val json = mapper.writeValueAsString(subPart)

        it("THEN JSON does not contain 'status' key") {
            json.contains("status") shouldBe false
        }

        it("THEN JSON does not contain 'iteration' key") {
            json.contains("iteration") shouldBe false
        }

        it("THEN JSON does not contain 'sessionIds' key") {
            json.contains("sessionIds") shouldBe false
        }
    }

    // ── FAIL_ON_UNKNOWN_PROPERTIES = false ──

    describe("GIVEN JSON with unknown properties") {
        val json = """{"max": 3, "current": 0, "unknownField": "value"}"""

        describe("WHEN deserializing IterationConfig") {
            val config = mapper.readValue<IterationConfig>(json)

            it("THEN ignores unknown field and deserializes correctly") {
                config shouldBe IterationConfig(max = 3, current = 0)
            }
        }
    }
})
