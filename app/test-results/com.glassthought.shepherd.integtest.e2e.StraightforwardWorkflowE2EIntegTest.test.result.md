---
spec: "com.glassthought.shepherd.integtest.e2e.StraightforwardWorkflowE2EIntegTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN a temp git repo with a simple ticket
  - WHEN running shepherd with straightforward workflow
    - [PASS] THEN .ai_out directory is created in the temp repo
    - [PASS] THEN a feature branch was created (not on initial branch)
    - [PASS] THEN the process exits with code 0
