# PortPublisher Interface Extraction — Private Context

## Status: COMPLETE

## Plan

1. [x] Read `PortFileManager.kt`, `HarnessServer.kt`, `KtorHarnessServerTest.kt`
2. [x] Add `PortPublisher` interface to `PortFileManager.kt` (above `PortFileManager` class)
3. [x] Make `PortFileManager : PortPublisher`, add `override` modifiers
4. [x] Update `KtorHarnessServer` constructor param and usages
5. [x] Update test named parameter
6. [x] Fix pre-existing bug: remove `afterSpec { httpClient.close() }` (invalid call — `OkHttpClient` has no `close()` method; was hidden by incremental compile cache)
7. [x] Tests pass: `BUILD SUCCESSFUL`

## Pre-existing Bug Discovered

`afterSpec { httpClient.close() }` in `KtorHarnessServerTest.kt` was invalid — `OkHttpClient` (v4.12.0) does not have a `close()` method. It compiled before only because the test file was never recompiled from scratch (incremental compilation cache). My changes to the test file (renaming the named param) caused a fresh recompile, surfacing the bug. Removed the call — no behavior change since `OkHttpClient` does not need explicit cleanup.

## Files Modified

- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt`
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt`
- `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`
