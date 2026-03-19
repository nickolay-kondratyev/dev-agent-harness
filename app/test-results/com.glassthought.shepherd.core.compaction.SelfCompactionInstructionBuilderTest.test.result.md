---
spec: "com.glassthought.shepherd.core.compaction.SelfCompactionInstructionBuilderTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN SelfCompactionInstructionBuilder
  - WHEN build is called with a PRIVATE.md path
    - [PASS] THEN contains guideline to preserve challenges and solutions
    - [PASS] THEN contains guideline to preserve codebase patterns
    - [PASS] THEN contains guideline to preserve current progress
    - [PASS] THEN contains guideline to preserve key decisions
    - [PASS] THEN contains guideline to preserve what we're doing and why
    - [PASS] THEN contains the callback signal script name
    - [PASS] THEN contains the full callback command
    - [PASS] THEN contains the self-compacted signal name
    - [PASS] THEN instructs the agent to make the summary concise
    - [PASS] THEN renders the correct PRIVATE.md absolute path
    - [PASS] THEN wraps the PRIVATE.md path in backticks
    - [PASS] THEN wraps the callback command in backticks
