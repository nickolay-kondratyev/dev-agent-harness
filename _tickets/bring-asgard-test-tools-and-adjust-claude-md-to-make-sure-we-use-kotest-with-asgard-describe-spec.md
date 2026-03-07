---
closed_iso: 2026-03-07T22:32:15Z
id: nid_gaypzj96k523r5rhy2db0lj2n_E
title: "bring asgard test tools and adjust claude md to make sure we use kotest with asgard describe spec"
status: closed
deps: []
links: []
created_iso: 2026-03-07T17:42:49Z
status_updated_iso: 2026-03-07T22:32:15Z
type: task
priority: 3
assignee: nickolaykondratyev
---

Bring in asgard test tools dependency to be able to use `asgardTestTools/src/commonMain/kotlin/com/asgard/testTools/describe_spec/AsgardDescribeSpec.kt` as the default describe spec.

With that we should update our instructions to use Kotest with AsgardDescribeSpec. AsgardDescribeSpec already has wiring with outFactory wired in and it detects things like WARNs being logged during testing.


--------------------------------------------------------------------------------
CLAUDE.md specifies BDD with Kotest DescribeSpec, but the project uses JUnit 5 with @Test.
All existing tests (InteractiveProcessRunnerTest, TmuxSessionManagerTest, TmuxCommunicatorTest) use JUnit 5. 
1. Add Kotest dependency and migrate tests to DescribeSpec
2. Update CLAUDE.md testing standards to reflect JUnit 5 usage


## Notes

**2026-03-07T22:32:22Z**

Completed: Added asgardTestTools:1.0.0 dependency via composite build substitution. Migrated all 4 test files (AppTest, InteractiveProcessRunnerTest, TmuxSessionManagerTest, TmuxCommunicatorTest) from JUnit 5 @Test to Kotest AsgardDescribeSpec with GIVEN/WHEN/THEN structure. Updated ai_input/memory/auto_load/4_testing_standards.md with Dependencies, Integration Tests, and Suspend Context sections. Regenerated CLAUDE.md. All 12 tests pass. Follow-up ticket nid_e6xmtbw1d539id72io3voxnxe_E created for duplicate test cleanup.
