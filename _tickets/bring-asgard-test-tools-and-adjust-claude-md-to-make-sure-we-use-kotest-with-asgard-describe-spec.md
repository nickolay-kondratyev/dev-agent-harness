---
id: nid_gaypzj96k523r5rhy2db0lj2n_E
title: "bring asgard test tools and adjust claude md to make sure we use kotest with asgard describe spec"
status: open
deps: []
links: []
created_iso: 2026-03-07T17:42:49Z
status_updated_iso: 2026-03-07T17:42:49Z
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

