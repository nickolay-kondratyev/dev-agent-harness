package com.glassthought.shepherd.core.filestructure

import com.asgard.testTools.describe_spec.AsgardDescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

class AiOutputStructureTest : AsgardDescribeSpec({

    describe("GIVEN AiOutputStructure with branch 'my_branch'") {
        val repoRoot = Path.of("/repo")
        val structure = AiOutputStructure(repoRoot = repoRoot, branch = "my_branch")

        // -- branch root ----------------------------------------------------

        describe("WHEN branchRoot is called") {
            it("THEN returns .ai_out/my_branch") {
                structure.branchRoot() shouldBe Path.of("/repo/.ai_out/my_branch")
            }
        }

        // -- harness_private/ -----------------------------------------------

        describe("WHEN harnessPrivateDir is called") {
            it("THEN returns .ai_out/my_branch/harness_private") {
                structure.harnessPrivateDir() shouldBe Path.of("/repo/.ai_out/my_branch/harness_private")
            }
        }

        describe("WHEN currentStateJson is called") {
            it("THEN returns harness_private/current_state.json") {
                structure.currentStateJson() shouldBe
                    Path.of("/repo/.ai_out/my_branch/harness_private/current_state.json")
            }
        }

        describe("WHEN planFlowJson is called") {
            it("THEN returns harness_private/plan_flow.json") {
                structure.planFlowJson() shouldBe
                    Path.of("/repo/.ai_out/my_branch/harness_private/plan_flow.json")
            }
        }

        // -- shared/plan/ ---------------------------------------------------

        describe("WHEN sharedPlanDir is called") {
            it("THEN returns .ai_out/my_branch/shared/plan") {
                structure.sharedPlanDir() shouldBe Path.of("/repo/.ai_out/my_branch/shared/plan")
            }
        }

        describe("WHEN planMd is called") {
            it("THEN returns shared/plan/PLAN.md") {
                structure.planMd() shouldBe Path.of("/repo/.ai_out/my_branch/shared/plan/PLAN.md")
            }
        }

        // -- planning sub-part paths ----------------------------------------

        describe("WHEN planningSubPartDir is called with 'plan'") {
            it("THEN returns .ai_out/my_branch/planning/plan") {
                structure.planningSubPartDir("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan")
            }
        }

        describe("WHEN planningSubPartPrivateDir is called with 'plan'") {
            it("THEN returns planning/plan/private") {
                structure.planningSubPartPrivateDir("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/private")
            }
        }

        describe("WHEN planningPrivateMd is called with 'plan'") {
            it("THEN returns planning/plan/private/PRIVATE.md") {
                structure.planningPrivateMd("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/private/PRIVATE.md")
            }
        }

        describe("WHEN planningCommInDir is called with 'plan'") {
            it("THEN returns planning/plan/comm/in") {
                structure.planningCommInDir("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/comm/in")
            }
        }

        describe("WHEN planningCommOutDir is called with 'plan'") {
            it("THEN returns planning/plan/comm/out") {
                structure.planningCommOutDir("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/comm/out")
            }
        }

        describe("WHEN planningInstructionsMd is called with 'plan'") {
            it("THEN returns planning/plan/comm/in/instructions.md") {
                structure.planningInstructionsMd("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/comm/in/instructions.md")
            }
        }

        describe("WHEN planningPublicMd is called with 'plan'") {
            it("THEN returns planning/plan/comm/out/PUBLIC.md") {
                structure.planningPublicMd("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/comm/out/PUBLIC.md")
            }
        }

        // -- execution part-level paths -------------------------------------

        describe("WHEN executionPartDir is called with 'backend'") {
            it("THEN returns .ai_out/my_branch/execution/backend") {
                structure.executionPartDir("backend") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend")
            }
        }

        describe("WHEN executionSubPartDir is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl") {
                structure.executionSubPartDir("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl")
            }
        }

        // -- feedback dirs (at part level) ----------------------------------

        describe("WHEN feedbackDir is called with 'backend'") {
            it("THEN returns execution/backend/__feedback") {
                structure.feedbackDir("backend") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/__feedback")
            }
        }

        describe("WHEN feedbackPendingDir is called with 'backend'") {
            it("THEN returns execution/backend/__feedback/pending") {
                structure.feedbackPendingDir("backend") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/__feedback/pending")
            }
        }

        describe("WHEN feedbackAddressedDir is called with 'backend'") {
            it("THEN returns execution/backend/__feedback/addressed") {
                structure.feedbackAddressedDir("backend") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/__feedback/addressed")
            }
        }

        describe("WHEN feedbackRejectedDir is called with 'backend'") {
            it("THEN returns execution/backend/__feedback/rejected") {
                structure.feedbackRejectedDir("backend") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/__feedback/rejected")
            }
        }

        // -- execution sub-part internals -----------------------------------

        describe("WHEN executionSubPartPrivateDir is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl/private") {
                structure.executionSubPartPrivateDir("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/private")
            }
        }

        describe("WHEN executionPrivateMd is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl/private/PRIVATE.md") {
                structure.executionPrivateMd("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/private/PRIVATE.md")
            }
        }

        describe("WHEN executionCommInDir is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl/comm/in") {
                structure.executionCommInDir("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/comm/in")
            }
        }

        describe("WHEN executionCommOutDir is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl/comm/out") {
                structure.executionCommOutDir("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/comm/out")
            }
        }

        describe("WHEN executionInstructionsMd is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl/comm/in/instructions.md") {
                structure.executionInstructionsMd("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/comm/in/instructions.md")
            }
        }

        describe("WHEN executionPublicMd is called with 'backend', 'impl'") {
            it("THEN returns execution/backend/impl/comm/out/PUBLIC.md") {
                structure.executionPublicMd("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/comm/out/PUBLIC.md")
            }
        }
    }

    // -- planning vs execution structural difference ------------------------

    describe("GIVEN AiOutputStructure — planning vs execution structural difference") {
        val structure = AiOutputStructure(repoRoot = Path.of("/repo"), branch = "my_branch")

        describe("WHEN comparing planningPublicMd and executionPublicMd") {
            it("THEN planning has no part-level grouping") {
                // planning: planning/${subPart}/comm/out/PUBLIC.md
                structure.planningPublicMd("plan") shouldBe
                    Path.of("/repo/.ai_out/my_branch/planning/plan/comm/out/PUBLIC.md")
            }

            it("THEN execution has part-level grouping") {
                // execution: execution/${part}/${subPart}/comm/out/PUBLIC.md
                structure.executionPublicMd("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/my_branch/execution/backend/impl/comm/out/PUBLIC.md")
            }
        }
    }

    // -- branch with slashes ------------------------------------------------

    describe("GIVEN AiOutputStructure with branch containing slashes 'feature/my-ticket'") {
        val structure = AiOutputStructure(repoRoot = Path.of("/repo"), branch = "feature/my-ticket")

        describe("WHEN branchRoot is called") {
            it("THEN slashes create nested path segments") {
                structure.branchRoot() shouldBe Path.of("/repo/.ai_out/feature/my-ticket")
            }
        }

        describe("WHEN planningPublicMd is called with 'plan'") {
            it("THEN full path includes nested branch segments") {
                structure.planningPublicMd("plan") shouldBe
                    Path.of("/repo/.ai_out/feature/my-ticket/planning/plan/comm/out/PUBLIC.md")
            }
        }

        describe("WHEN feedbackPendingDir is called with 'backend'") {
            it("THEN full path includes nested branch segments") {
                structure.feedbackPendingDir("backend") shouldBe
                    Path.of("/repo/.ai_out/feature/my-ticket/execution/backend/__feedback/pending")
            }
        }

        describe("WHEN executionPublicMd is called with 'backend', 'impl'") {
            it("THEN full path includes nested branch segments") {
                structure.executionPublicMd("backend", "impl") shouldBe
                    Path.of("/repo/.ai_out/feature/my-ticket/execution/backend/impl/comm/out/PUBLIC.md")
            }
        }
    }
})
