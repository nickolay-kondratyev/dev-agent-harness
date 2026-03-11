---
spec: "com.glassthought.chainsaw.core.agent.DefaultAgentTypeChooserTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN DefaultAgentTypeChooser
  - WHEN choose is called with phaseType=IMPLEMENTOR
    - [PASS] THEN returns CLAUDE_CODE
  - WHEN choose is called with phaseType=PLANNER
    - [PASS] THEN returns CLAUDE_CODE
  - WHEN choose is called with phaseType=PLAN_REVIEWER
    - [PASS] THEN returns CLAUDE_CODE
  - WHEN choose is called with phaseType=REVIEWER
    - [PASS] THEN returns CLAUDE_CODE
