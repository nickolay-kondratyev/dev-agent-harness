# Exploration Summary: HealthTimeoutLadder Refactor

## Current State

### HarnessTimeoutConfig (flat fields to refactor)
- `startupAckTimeout: Duration = 3.minutes`
- `noActivityTimeout: Duration = 30.minutes`
- `pingTimeout: Duration = 3.minutes`
- `healthCheckInterval: Duration = 5.minutes` ← stays separate (polling interval)

### Files to Modify
1. `app/src/main/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfig.kt` — create HealthTimeoutLadder, replace flat fields
2. `app/src/test/kotlin/com/glassthought/shepherd/core/data/HarnessTimeoutConfigTest.kt` — update assertions

### No Other Consumers
- No main code references these flat fields yet (only test + config definition)
- ShepherdContext holds `HarnessTimeoutConfig` but doesn't reference individual timeout fields
- Safe, low-risk refactor

### Target from Spec (doc/use-case/HealthMonitoring.md)
```kotlin
data class HealthTimeoutLadder(
    val startup: Duration = 3.minutes,
    val normalActivity: Duration = 30.minutes,
    val pingResponse: Duration = 3.minutes
)
```
Access: `HarnessTimeoutConfig.healthTimeouts: HealthTimeoutLadder`
Test: `HealthTimeoutLadder(startup = 1.second, normalActivity = 5.seconds, pingResponse = 1.second)`

### forTests() currently uses
- startupAckTimeout = 2.seconds → spec says 1.second
- noActivityTimeout = 5.seconds → stays 5.seconds
- pingTimeout = 2.seconds → spec says 1.second
