---
spec: "com.glassthought.shepherd.core.data.HarnessTimeoutConfigTest"
status: PASSED
failed: 0
skipped: 0
---

- GIVEN HarnessTimeoutConfig.defaults()
  - [PASS] THEN contextWindowHardThresholdPct is 20
  - [PASS] THEN contextWindowSoftThresholdPct is 35
  - [PASS] THEN healthCheckInterval is 5 minutes
  - [PASS] THEN payloadAckMaxAttempts is 3
  - [PASS] THEN payloadAckTimeout is 3 minutes
  - [PASS] THEN selfCompactionTimeout is 5 minutes
  - WHEN inspecting healthTimeouts ladder
    - [PASS] THEN normalActivity is 30 minutes
    - [PASS] THEN pingResponse is 3 minutes
    - [PASS] THEN startup is 3 minutes
- GIVEN HarnessTimeoutConfig.forTests()
  - [PASS] THEN contextWindowHardThresholdPct retains production value
  - [PASS] THEN contextWindowSoftThresholdPct retains production value
  - [PASS] THEN selfCompactionTimeout is shorter than production default
  - WHEN inspecting healthTimeouts ladder
    - [PASS] THEN normalActivity is 5 seconds
    - [PASS] THEN pingResponse is 1 second
    - [PASS] THEN startup is 1 second
- GIVEN HealthTimeoutLadder with default values
  - [PASS] THEN normalActivity defaults to 30 minutes
  - [PASS] THEN pingResponse defaults to 3 minutes
  - [PASS] THEN startup defaults to 3 minutes
