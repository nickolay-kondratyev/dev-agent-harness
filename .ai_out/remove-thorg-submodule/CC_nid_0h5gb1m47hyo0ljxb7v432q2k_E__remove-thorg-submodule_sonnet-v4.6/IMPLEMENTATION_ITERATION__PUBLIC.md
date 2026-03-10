# Implementation Iteration: Review Fix Pass

**Ticket:** nid_0h5gb1m47hyo0ljxb7v432q2k_E
**Date:** 2026-03-10
**Status:** COMPLETE — all 5 review issues resolved, all verifications green

---

## Issues Fixed

### Fix 1 (CRITICAL): Committed submodule changes

The three files modified in `submodules/thorg-root` were uncommitted, making them invisible to git and breaking fresh-clone setups.

**Action taken:** Committed in submodule as `553eb324f`:
- `source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-multiplatform.gradle.kts`
- `source/libraries/kotlin-mp/build-logic/src/main/kotlin/buildlogic.kotlin-jvm.gradle.kts`
- `source/libraries/kotlin-mp/thorgKotlinMP.build.gradle.kts`

Parent repo submodule pointer updated in commit `5f7f28b`.

### Fix 2 (IMPORTANT): mavenLocal() ordering in app/build.gradle.kts

`mavenCentral()` was listed before `mavenLocal()`, inconsistent with the convention in `buildlogic.kotlin-multiplatform.gradle.kts`.

**Action taken:** Reordered to `mavenLocal()` first in `/app/build.gradle.kts`. Updated comment to explain rationale (consistency + prevents remote artifacts shadowing local ones).

### Fix 3 (IMPORTANT): checkAsgardInMavenLocal now throws on missing artifacts

The task was printing a warning but returning exit 0 when artifacts were missing — violating Gradle check task convention.

**Action taken:** Added `throw GradleException(...)` in the missing branch of `build.gradle.kts`. Task now fails properly, making it usable as a build gate.

### Fix 4 (IMPORTANT): Guard for uninitialized submodule in publishAsgardToMavenLocal

If the submodule was not initialized, `ProcessBuilder.start()` would throw a raw `IOException` with a cryptic OS error instead of a clear message.

**Action taken:** Added `if (!kotlinMpDir.exists()) { throw GradleException("Submodule not initialized. Run: git submodule update --init") }` before the `ProcessBuilder` call in `build.gradle.kts`.

### Fix 5 (IMPORTANT): Non-UUID anchor points replaced

The two `@AnchorPoint` annotations used descriptive names instead of UUIDs, violating the `ap.UUID.E` convention.

**Action taken:** Generated two real UUIDs via `anchor_point.create` and replaced the descriptive names:
- `anchor_point.publishAsgardToMavenLocal.E` → `ap.MtB03DtelNNjPmY0VjKHs.E`
- Second anchor point → `ap.luMV9nN9bCUVxYfZkAVYR.E`

---

## Verification Results

### Build without THORG_ROOT
```
unset THORG_ROOT && ./gradlew :app:build :app:test
EXIT_CODE=0
```

### checkAsgardInMavenLocal
```
./gradlew checkAsgardInMavenLocal
> Task :checkAsgardInMavenLocal
asgard libraries are present in maven local.
BUILD SUCCESSFUL
EXIT_CODE=0
```

---

## Files Changed

- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/build.gradle.kts` — UUID anchor points, submodule guard, checkAsgardInMavenLocal throws
- `/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/app/build.gradle.kts` — mavenLocal() before mavenCentral()
- `submodules/thorg-root` (submodule commit `553eb324f`) — three build files committed
