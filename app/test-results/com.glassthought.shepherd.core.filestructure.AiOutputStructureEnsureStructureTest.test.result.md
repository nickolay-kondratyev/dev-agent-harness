---
spec: "com.glassthought.shepherd.core.filestructure.AiOutputStructureEnsureStructureTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN ensureStructure called twice with same parts
  - [PASS] THEN execution/backend/__feedback/pending still exists
  - [PASS] THEN execution/backend/impl/private still exists
  - [PASS] THEN harness_private still exists
  - [PASS] THEN planning/plan/private still exists
- GIVEN ensureStructure with only execution parts
  - [PASS] THEN execution/backend/__feedback/pending exists
  - [PASS] THEN execution/backend/impl/comm/in exists
  - [PASS] THEN execution/frontend/__feedback/rejected exists
  - [PASS] THEN execution/frontend/review/private exists
  - [PASS] THEN harness_private exists
  - [PASS] THEN planning directory does NOT exist
  - [PASS] THEN shared/plan exists
- GIVEN ensureStructure with only planning parts
  - [PASS] THEN execution directory does NOT exist
  - [PASS] THEN harness_private exists
  - [PASS] THEN no __feedback directory exists anywhere
  - [PASS] THEN planning/design/comm/in exists
  - [PASS] THEN planning/design/private exists
  - [PASS] THEN planning/review/comm/out exists
  - [PASS] THEN shared/plan exists
- GIVEN ensureStructure with planning + execution parts
  - WHEN checking always-present directories
    - [PASS] THEN harness_private exists
    - [PASS] THEN shared/plan exists
  - WHEN checking execution part feedback directories
    - [PASS] THEN execution/backend/__feedback/addressed exists
    - [PASS] THEN execution/backend/__feedback/pending exists
    - [PASS] THEN execution/backend/__feedback/rejected exists
  - WHEN checking execution sub-part directories
    - [PASS] THEN execution/backend/impl/comm/in exists
    - [PASS] THEN execution/backend/impl/comm/out exists
    - [PASS] THEN execution/backend/impl/private exists
    - [PASS] THEN execution/backend/review/comm/in exists
    - [PASS] THEN execution/backend/review/comm/out exists
    - [PASS] THEN execution/backend/review/private exists
  - WHEN checking planning sub-part directories
    - [PASS] THEN planning/plan/comm/in exists
    - [PASS] THEN planning/plan/comm/out exists
    - [PASS] THEN planning/plan/private exists
  - WHEN checking that planning dirs do NOT have __feedback
    - [PASS] THEN no __feedback directory exists under planning
    - [PASS] THEN no __feedback directory exists under planning sub-part
