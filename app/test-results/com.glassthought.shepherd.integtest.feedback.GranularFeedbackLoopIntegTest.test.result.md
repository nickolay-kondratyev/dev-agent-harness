---
spec: "com.glassthought.shepherd.integtest.feedback.GranularFeedbackLoopIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- (1) GIVEN doer rejects a feedback item
  - WHEN reviewer insists and doer complies with ADDRESSED
    - [PASS] THEN file moves to addressed/ directory
- GIVEN an optional feedback file
  - WHEN doer writes SKIPPED on a critical item
    - [PASS] THEN result is Terminate(AgentCrashed)
  - WHEN doer writes SKIPPED resolution
    - [PASS] THEN file moves to addressed/ directory
- GIVEN critical (addressed), important (rejected+accepted), optional (skipped)
  - WHEN inner loop processes all three
    - [PASS] THEN critical and optional land in addressed/
    - [PASS] THEN git commit is called once per feedback item
    - [PASS] THEN pending/ is empty
    - [PASS] THEN rejected important item lands in rejected/
    - [PASS] THEN result is Continue
- GIVEN critical feedback files remain in pending/
  - WHEN PartCompletionGuard validates on reviewer PASS
    - [PASS] THEN guard fails with message about unaddressed items
    - [PASS] THEN guard passes when only optional items remain in pending
    - [PASS] THEN guard passes when pending is empty
- GIVEN doer rejects a feedback item
  - WHEN reviewer accepts the rejection (signals PASS)
    - [PASS] THEN file moves to rejected/ directory
- GIVEN pending/ directory is empty after reviewer signals needs_iteration
  - WHEN inner feedback loop executes
    - [PASS] THEN result is Terminate(AgentCrashed)
- GIVEN reviewer writes critical, important, and optional feedback
  - WHEN doer addresses every item with ADDRESSED resolution
    - [PASS] THEN all files move to addressed/ in severity order
    - [PASS] THEN git commit is called once per feedback item
    - [PASS] THEN iteration counter in git commits remains constant
    - [PASS] THEN pending/ is empty
    - [PASS] THEN result is Continue
