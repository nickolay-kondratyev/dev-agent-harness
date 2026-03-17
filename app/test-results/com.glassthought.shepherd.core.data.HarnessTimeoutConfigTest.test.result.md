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
  - [PASS] THEN noActivityTimeout is 30 minutes
  - [PASS] THEN payloadAckRetries is 3
  - [PASS] THEN payloadAckTimeout is 3 minutes
  - [PASS] THEN pingTimeout is 3 minutes
  - [PASS] THEN selfCompactionTimeout is 5 minutes
  - [PASS] THEN startupAckTimeout is 3 minutes
- GIVEN HarnessTimeoutConfig.forTests()
  - [PASS] THEN contextWindowHardThresholdPct retains production value
  - [PASS] THEN contextWindowSoftThresholdPct retains production value
  - [PASS] THEN noActivityTimeout is shorter than production default
  - [PASS] THEN selfCompactionTimeout is shorter than production default
  - [PASS] THEN startupAckTimeout is shorter than production default
