# Detailed Implementation Plan: Harness HTTP Server

## 1. Problem Understanding

**Goal**: Create a Ktor CIO HTTP server that agents (running in TMUX sessions) use to communicate back to the Chainsaw harness. This is a **stub server** -- endpoints accept requests, log them, and return 200. No real handler logic yet.

**Key Constraints**:
- Port 0 binding (OS-assigned), port written to `$HOME/.chainsaw_agent_harness/server/port.txt`
- Port file deleted on shutdown
- 4 POST endpoint stubs: `/agent/done`, `/agent/question`, `/agent/failed`, `/agent/status`
- All endpoints accept JSON with at minimum a `branch` field
- Must implement `AsgardCloseable` for lifecycle management
- Constructor injection, `Out`/`OutFactory` logging, suspend methods
- Package: `com.glassthought.chainsaw.core.server`

**Assumptions**:
- Ktor 3.4.1 (latest stable) is compatible with Kotlin 2.2.20 used in this project
- Dependencies are declared inline in `build.gradle.kts` (matching existing pattern -- no version catalog entries for Ktor)
- The server runs on `localhost` only (agents are local TMUX sessions)
- Tests use real HTTP clients against the actual bound port (not Ktor's `testApplication`) since we need to verify the real port file workflow

## 2. High-Level Architecture

```
Agent (TMUX session)
  |
  | harness-cli-for-agent.sh reads port from port.txt
  | curl POST http://localhost:{port}/agent/{endpoint}
  v
KtorHarnessServer (Ktor CIO, port 0)
  |
  | Parses JSON via ContentNegotiation + Jackson
  | Logs request via Out
  | Returns 200
  |
  | On start: writes port to port.txt
  | On close: stops engine + deletes port.txt
  v
HarnessServer interface (AsgardCloseable)
```

**Components**:
1. `HarnessServer` -- interface extending `AsgardCloseable` with `start()` and `port()` methods
2. `KtorHarnessServer` -- Ktor CIO implementation
3. `PortFileManager` -- encapsulates port file write/delete logic (SRP: separate from server concerns)
4. Request data classes -- `AgentDoneRequest`, `AgentQuestionRequest`, `AgentFailedRequest`, `AgentStatusRequest`

## 3. Files to Create/Modify

### New Files

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/glassthought/chainsaw/core/server/HarnessServer.kt` | Interface + `KtorHarnessServer` implementation |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/server/AgentRequests.kt` | Request data classes for JSON deserialization |
| `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt` | Port file write/delete logic |
| `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt` | Unit/integration tests |

### Modified Files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add Ktor CIO server + content negotiation + Jackson serialization dependencies |

## 4. Dependencies to Add

Add to `app/build.gradle.kts` `dependencies` block:

```kotlin
// Ktor: embedded HTTP server for agent-to-harness communication
implementation("io.ktor:ktor-server-core:3.4.1")
implementation("io.ktor:ktor-server-cio:3.4.1")
implementation("io.ktor:ktor-server-content-negotiation:3.4.1")
implementation("io.ktor:ktor-serialization-jackson:3.4.1")

// Ktor test support (for OkHttp-based integration testing against real server)
// Note: We use OkHttp (already a dependency) as the HTTP client in tests,
// NOT ktor-server-test-host, because we need to test the real port-file workflow.
```

**Rationale for NOT using `ktor-server-test-host`**: The core behavior under test is that the server binds a real OS port, writes it to a file, and can receive HTTP requests on that port. The Ktor test host bypasses all of this. We already have OkHttp as a dependency, so using it as the test client is natural and tests the real end-to-end flow.

**Note on Jackson**: `ktor-serialization-jackson` brings in its own Jackson dependency. The project already has `jackson-databind:2.17.2` and `jackson-module-kotlin:2.17.2`. Ktor 3.4.1 uses Jackson 2.17.x as well, so there should be no version conflict. If Gradle reports a conflict, align to the higher version.

## 5. Class/Interface Design

### 5.1 `HarnessServer` Interface

```kotlin
package com.glassthought.chainsaw.core.server

import com.asgard.core.lifecycle.AsgardCloseable

/**
 * HTTP server for agent-to-harness communication.
 *
 * Agents call endpoints via harness-cli-for-agent.sh (ref.ap.8PB8nMd93D3jipEWhME5n.E).
 * The server binds to an OS-assigned port and publishes it via a port file.
 */
interface HarnessServer : AsgardCloseable {
    /** Starts the server, binds to a port, and writes the port file. */
    suspend fun start()

    /** Returns the bound port. Throws if the server has not been started. */
    fun port(): Int
}
```

### 5.2 `KtorHarnessServer` Implementation

Constructor parameters:
- `outFactory: OutFactory` -- for structured logging
- `portFileManager: PortFileManager` -- for port file write/delete

Key implementation details:
- Create `embeddedServer(CIO, port = 0)` with routing module
- After `engine.start(wait = false)`, resolve the actual port via `engine.resolvedConnectors().first().port`
- Call `portFileManager.writePort(port)` after start
- `close()`: call `engine.stop()` then `portFileManager.deletePort()`
- Each endpoint: receive JSON body, log via `Out`, respond 200 OK

### 5.3 Request Data Classes (`AgentRequests.kt`)

```kotlin
package com.glassthought.chainsaw.core.server

/** Base fields present in every agent request. */
data class AgentDoneRequest(val branch: String)

data class AgentQuestionRequest(val branch: String, val question: String)

data class AgentFailedRequest(val branch: String, val reason: String)

data class AgentStatusRequest(val branch: String)
```

These are simple `data class`es with val properties. Jackson + Kotlin module will handle deserialization. No inheritance needed -- these are stubs and each endpoint has its own type for type safety even though some share the same shape today.

### 5.4 `PortFileManager`

```kotlin
package com.glassthought.chainsaw.core.server

import java.nio.file.Path

/**
 * Manages the port file that agents read to discover the harness server port.
 *
 * Default path: $HOME/.chainsaw_agent_harness/server/port.txt
 * (matches ref.ap.8PB8nMd93D3jipEWhME5n.E harness-cli-for-agent.sh)
 */
interface PortFileManager {
    fun writePort(port: Int)
    fun deletePort()
    fun portFilePath(): Path

    companion object {
        fun standard(): PortFileManager = PortFileManagerImpl()
        fun withCustomPath(portFilePath: Path): PortFileManager = PortFileManagerImpl(portFilePath)
    }
}
```

Implementation (`PortFileManagerImpl`, private class in the same file):
- Default path: `Path.of(System.getProperty("user.home"), ".chainsaw_agent_harness", "server", "port.txt")`
- `writePort`: create parent directories, write port as string (just the number, no trailing newline -- matches what the shell script's `read -r` expects)
- `deletePort`: delete the file if it exists (idempotent)
- Constructor accepts optional `Path` override for testability

## 6. Endpoint Routing Design

All endpoints are under the `/agent` path prefix. Each:
1. Receives POST with JSON body
2. Deserializes into the corresponding request data class
3. Logs the request receipt via `Out` with structured `Val` values
4. Returns `200 OK` with a simple JSON acknowledgment body `{"status": "ok"}`

```
POST /agent/done     -> AgentDoneRequest     -> log -> 200 {"status": "ok"}
POST /agent/question -> AgentQuestionRequest  -> log -> 200 {"status": "ok"}
POST /agent/failed   -> AgentFailedRequest    -> log -> 200 {"status": "ok"}
POST /agent/status   -> AgentStatusRequest    -> log -> 200 {"status": "ok"}
```

The routing is installed in a Ktor `Application.module()` extension function defined within `KtorHarnessServer`. The module:
1. Installs `ContentNegotiation` with `jackson { registerModule(KotlinModule.Builder().build()) }`
2. Installs `Routing` with the 4 POST endpoints

**Note**: Jackson's Kotlin module is required for `data class` deserialization. It is already on the classpath via the existing `jackson-module-kotlin` dependency, but Ktor's `jackson {}` block should explicitly register it.

## 7. Port File Management Approach

**Write flow**:
1. `KtorHarnessServer.start()` starts the Ktor engine with `port = 0`
2. After `engine.start(wait = false)`, the OS assigns a port
3. Retrieve port via `engine.resolvedConnectors().first().port`
4. Call `portFileManager.writePort(port)` which:
   - Creates `$HOME/.chainsaw_agent_harness/server/` directory (if missing)
   - Writes the port number as a plain string to `port.txt`

**Delete flow**:
1. `KtorHarnessServer.close()` calls `engine.stop(gracePeriodMillis, timeoutMillis)`
2. Then calls `portFileManager.deletePort()` which deletes the file

**Ordering matters**: Write AFTER engine starts (so port is valid). Delete AFTER engine stops (so agents don't try to connect to a dead server using a stale port file -- though in practice there is a small race window; this is acceptable for V1).

**Test path override**: `PortFileManager.withCustomPath(tempPath)` allows tests to use a temp directory instead of the real `$HOME` path, avoiding pollution of the developer's home directory.

## 8. Implementation Phases

### Phase 1: Add Dependencies
- **Goal**: Add Ktor dependencies to `build.gradle.kts`, verify the project compiles
- **Components**: `app/build.gradle.kts`
- **Steps**:
  1. Add Ktor CIO, content negotiation, and Jackson serialization deps
  2. Run `./gradlew :app:build` -- must pass
- **Verification**: Build succeeds with new deps

### Phase 2: Create PortFileManager
- **Goal**: Encapsulate port file read/write logic
- **Components**: `PortFileManager.kt`
- **Steps**:
  1. Create interface + implementation
  2. Write unit test: `writePort` creates file with correct content, `deletePort` removes it
- **Dependencies**: None (pure file I/O)
- **Verification**: Unit tests pass

### Phase 3: Create Request Data Classes
- **Goal**: Define JSON request structures
- **Components**: `AgentRequests.kt`
- **Steps**:
  1. Create the 4 data classes
- **Dependencies**: None
- **Verification**: Compiles

### Phase 4: Create HarnessServer Interface + KtorHarnessServer
- **Goal**: Implement the server with all 4 endpoint stubs
- **Components**: `HarnessServer.kt`
- **Steps**:
  1. Define `HarnessServer` interface extending `AsgardCloseable`
  2. Implement `KtorHarnessServer` with Ktor CIO engine
  3. Install ContentNegotiation with Jackson
  4. Define routing with 4 POST endpoints
  5. Implement `start()` (engine start + port file write)
  6. Implement `close()` (engine stop + port file delete)
  7. Implement `port()` accessor
- **Dependencies**: Phases 1, 2, 3
- **Verification**: Compiles

### Phase 5: Write Tests
- **Goal**: Verify server lifecycle and endpoint behavior
- **Components**: `KtorHarnessServerTest.kt`
- **Steps**: See Test Plan below
- **Dependencies**: Phase 4
- **Verification**: All tests pass via `./gradlew :app:test`

### Phase 6: Wire into AppDependencies (optional, likely separate ticket)
- **Goal**: Add `HarnessServer` to `AppDependencies` and `Initializer`
- **Note**: This is called out for awareness. Whether to do it in this ticket or defer depends on scope agreement. The server can be independently tested without wiring.

## 9. Test Plan

Test file: `app/src/test/kotlin/com/glassthought/chainsaw/core/server/KtorHarnessServerTest.kt`

Tests use a real `KtorHarnessServer` instance with a temp directory for the port file. OkHttp is the HTTP client (already a project dependency).

### Test Fixture

```
data class ServerTestFixture(
    val server: KtorHarnessServer,
    val portFilePath: Path,
    val httpClient: OkHttpClient,
)
```

A helper `withServer` function:
1. Creates a temp directory for the port file
2. Creates `PortFileManager.withCustomPath(tempPortFilePath)`
3. Creates and starts `KtorHarnessServer`
4. Runs the test block
5. Closes the server in `finally`
6. Cleans up temp directory

### Test Cases (BDD Structure)

```
describe("GIVEN a started KtorHarnessServer") {

    describe("AND port file management") {

        it("THEN port file exists after start") {
            // Assert: tempPortFilePath file exists
        }

        it("THEN port file contains the actual bound port as a number") {
            // Read port from file, parse as Int, assert equals server.port()
        }

        it("THEN bound port is in valid TCP range (1-65535)") {
            // Assert: server.port() in 1..65535
        }
    }

    describe("AND POST /agent/done is called with valid JSON") {

        it("THEN response status is 200") {
            // POST {"branch": "test-branch"} to http://localhost:{port}/agent/done
            // Assert: response code == 200
        }
    }

    describe("AND POST /agent/question is called with valid JSON") {

        it("THEN response status is 200") {
            // POST {"branch": "test-branch", "question": "How?"} to /agent/question
            // Assert: response code == 200
        }
    }

    describe("AND POST /agent/failed is called with valid JSON") {

        it("THEN response status is 200") {
            // POST {"branch": "test-branch", "reason": "Out of memory"} to /agent/failed
            // Assert: response code == 200
        }
    }

    describe("AND POST /agent/status is called with valid JSON") {

        it("THEN response status is 200") {
            // POST {"branch": "test-branch"} to /agent/status
            // Assert: response code == 200
        }
    }

    describe("AND the server is closed") {

        it("THEN port file is deleted after close") {
            // Start server, close it, assert port file does NOT exist
        }
    }
}
```

### PortFileManager Tests (separate describe or separate file)

```
describe("GIVEN PortFileManager with a temp directory") {

    describe("WHEN writePort is called") {

        it("THEN port file exists") { ... }

        it("THEN port file content is the port number as string") { ... }
    }

    describe("WHEN deletePort is called after writePort") {

        it("THEN port file does not exist") { ... }
    }

    describe("WHEN deletePort is called without prior writePort") {

        it("THEN does not throw") { ... }
    }
}
```

**Total**: ~12 focused `it` blocks, each with one assertion.

**Test classification**: These are NOT integration tests (no external dependencies like tmux, network APIs). The server binds to localhost port 0 -- this is pure in-process. They should run as regular unit tests (no `isIntegTestEnabled` gating needed).

## 10. Technical Considerations

### Ktor Engine Port Resolution
After `engine.start(wait = false)`, the actual port is available via:
```kotlin
val port = engine.resolvedConnectors().first().port
```
This is the standard Ktor 3 API for resolving the port when using port 0.

### Thread Safety
- `KtorHarnessServer` should guard against double-start and use-after-close with an `AtomicReference` or similar state tracking
- The Ktor CIO engine itself is thread-safe

### Graceful Shutdown
Use `engine.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)` for orderly connection draining.

### Error Handling
- If the port file directory cannot be created, let the `IOException` propagate (fail fast)
- If `start()` is called twice, throw `IllegalStateException`
- If `port()` is called before `start()`, throw `IllegalStateException`

### Logging
- On start: log `harness_server_started` with `Val(port, ValType.PORT)` (add `PORT` to `ValType` if not present, or use `ValType.NUMBER`)
- On each endpoint hit: log `agent_request_received` with `Val(endpoint, ValType.URL_PATH)` and `Val(branch, ValType.STRING_USER_AGNOSTIC)`
- On close: log `harness_server_stopped`

### ValType
Check existing `ValType` enum values. If `PORT` or `URL_PATH` don't exist, use the closest semantic match (likely `NUMBER` and `STRING_USER_AGNOSTIC` respectively). Do NOT add new `ValType` values unless they are clearly reusable beyond this feature.

## 11. Acceptance Criteria (Automated)

All of the following must be true when running `./gradlew :app:test`:

1. `KtorHarnessServer` starts successfully and binds to a valid port (1-65535)
2. Port file is created at the specified path containing the correct port number
3. `POST /agent/done` with `{"branch": "test"}` returns HTTP 200
4. `POST /agent/question` with `{"branch": "test", "question": "q"}` returns HTTP 200
5. `POST /agent/failed` with `{"branch": "test", "reason": "r"}` returns HTTP 200
6. `POST /agent/status` with `{"branch": "test"}` returns HTTP 200
7. After `close()`, the port file no longer exists
8. Full build passes: `./gradlew :app:build`
9. No existing tests are broken

## 12. Open Questions / Decisions

1. **Wire into AppDependencies now or later?** -- Recommend deferring to a follow-up ticket. This keeps the scope of this ticket focused on the server itself. The wiring is trivial (add field to `AppDependencies`, create in `InitializerImpl`).

2. **ValType additions** -- If `ValType.PORT` does not exist, the implementer should check and either add it (if the enum is in this project) or use `ValType.NUMBER`. Same for `ValType.URL_PATH`.

3. **Response body format** -- The plan uses `{"status": "ok"}` as the response. The agent CLI (`harness-cli-for-agent.sh`) does not parse the response body (it only checks the HTTP status code via `--fail-with-body`). So the response body content is not critical, but having it be valid JSON is good practice.
