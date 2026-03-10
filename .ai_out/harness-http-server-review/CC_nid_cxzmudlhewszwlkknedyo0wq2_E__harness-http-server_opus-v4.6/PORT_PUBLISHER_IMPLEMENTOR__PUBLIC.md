# PortPublisher Interface Extraction — Public Summary

## What Changed

### 1. `PortFileManager.kt` — `PortPublisher` interface added

Added `PortPublisher` interface above `PortFileManager` in the same file (per project convention: interface and default impl in the same file):

```kotlin
interface PortPublisher {
    fun writePort(port: Int)
    fun deletePort()
}
```

`PortFileManager` now implements `PortPublisher`, and both `writePort` and `deletePort` are marked `override`.

### 2. `HarnessServer.kt` — `KtorHarnessServer` depends on `PortPublisher`

- Constructor parameter changed: `portFileManager: PortFileManager` → `portPublisher: PortPublisher`
- All internal usages updated: `portFileManager.writePort(...)` → `portPublisher.writePort(...)`, `portFileManager.deletePort()` → `portPublisher.deletePort()`
- KDoc `@param` updated accordingly

### 3. `KtorHarnessServerTest.kt` — named parameter updated

- `portFileManager = portFileManager` → `portPublisher = portFileManager`
- Also removed a pre-existing broken `afterSpec { httpClient.close() }` call. `OkHttpClient` does not implement `Closeable` and has no `close()` method. This line compiled before only due to an incremental compilation cache artifact — it would have failed on a clean build. Removing it is the correct fix; `OkHttpClient` resources are reclaimed by GC.

## Test Results

All tests in `com.glassthought.chainsaw.core.server.*` passed:

```
BUILD SUCCESSFUL in 2s
15 actionable tasks: 4 executed, 11 up-to-date
```
