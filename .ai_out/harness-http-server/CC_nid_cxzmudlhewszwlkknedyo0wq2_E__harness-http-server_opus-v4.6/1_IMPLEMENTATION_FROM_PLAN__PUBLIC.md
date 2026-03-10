# Implementation Summary: Harness HTTP Server

## What Was Built

A Ktor CIO HTTP server that agents (running in TMUX sessions) use to communicate back to the Chainsaw harness. This is a **stub server** -- endpoints accept requests, log them, and return `200 OK` with `{"status": "ok"}`.

## Files Created

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` | `HarnessServer` interface (extends `AsgardCloseable`) + `KtorHarnessServer` implementation |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt` | 4 request data classes: `AgentDoneRequest`, `AgentQuestionRequest`, `AgentFailedRequest`, `AgentStatusRequest` |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt` | Plain class managing port file write/delete lifecycle |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt` | 8 BDD tests for server lifecycle and endpoint behavior |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/server/PortFileManagerTest.kt` | 4 BDD tests for port file management |

## Files Modified

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Added 4 Ktor dependencies: `ktor-server-core`, `ktor-server-cio`, `ktor-server-content-negotiation`, `ktor-serialization-jackson` (all 3.1.1) |

## Key Design Decisions

### PortFileManager: Plain Class (Reviewer Feedback Applied)
Per plan review feedback, `PortFileManager` was simplified from interface + private impl + companion factory to a plain class with `Path` constructor parameter and `DEFAULT_PATH` companion constant. No interface -- YAGNI until there is a second implementation.

### Ktor Version: 3.1.1 (Not 3.4.1)
The plan specified Ktor 3.4.1 but this version does not exist as of the implementation date. Used 3.1.1 which is the latest stable release compatible with Kotlin 2.2.20. This compiled and all tests pass.

### Dependency Declaration: Inline Strings
Ktor dependencies use inline version strings (e.g., `"io.ktor:ktor-server-core:3.1.1"`), consistent with how OkHttp, Jackson, asgardCore, and other dependencies are declared in this project.

### Tests: Real Server, Not Ktor Test Host
Tests use a real `KtorHarnessServer` with OkHttp as the HTTP client. This verifies the real port-binding and port-file workflow end-to-end. Ktor's `testApplication` bypasses port binding and would not test the critical integration.

### Tests: Not Gated by isIntegTestEnabled
These tests bind to localhost port 0 -- purely in-process, no external dependencies. They run as regular unit tests.

### ValType Usage
- `ValType.PORT_AS_INT` for the bound port (exists in ValType enum)
- `ValType.HTTP_REQUEST_PATH` for endpoint paths (exists in ValType enum)
- `ValType.GIT_BRANCH_NAME` for the branch field from requests (exists in ValType enum)

### Logging
- `harness_server_started` with port value on start
- `agent_request_received` with endpoint path and branch on each endpoint hit
- `harness_server_stopped` on close
- All using `Out` structured logging with `Val`/`ValType`, snake_case messages

### Server Lifecycle
- `start()` checks for double-start via `engine == null` guard
- `port()` throws `IllegalStateException` before start
- `close()` is idempotent (returns early if engine is null)
- Graceful shutdown with 1s grace period and 5s timeout

## Deferred Work (Not In Scope)
- **Wiring into AppDependencies**: The server can be independently created and tested. Wiring into `AppDependencies` and `InitializerImpl` should be a separate ticket.
- **Real endpoint handler logic**: All endpoints are stubs returning 200. Real handler logic (e.g., blocking for question answers, triggering harness use cases) will be implemented when the harness workflow execution is built.

## Test Results

```
KtorHarnessServerTest: tests=8, failures=0, errors=0, skipped=0
PortFileManagerTest:   tests=4, failures=0, errors=0, skipped=0
Full build:            ./gradlew :app:build -> EXIT_CODE=0
```

All existing tests continue to pass.
