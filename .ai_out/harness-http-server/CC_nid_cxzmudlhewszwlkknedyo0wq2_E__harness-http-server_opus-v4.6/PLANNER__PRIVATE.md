# Planner Private Notes: Harness HTTP Server

## Research Findings

### Ktor Version Selection
- Latest stable: **Ktor 3.4.1** (released March 4, 2026)
- Compatible with Kotlin 2.2.20 (project's Kotlin version)
- Source: [Ktor GitHub releases](https://github.com/ktorio/ktor/releases)

### Ktor 3 API Notes
- Server creation: `embeddedServer(CIO, port = 0) { ... }`
- Port resolution after start: `engine.resolvedConnectors().first().port`
- Content negotiation: `install(ContentNegotiation) { jackson { registerModule(KotlinModule.Builder().build()) } }`
- Engine stop: `engine.stop(gracePeriodMillis, timeoutMillis)`
- Imports changed in Ktor 3 from Ktor 2 (e.g., `io.ktor.server.plugins.contentnegotiation.*` not `io.ktor.features.*`)
- Source: [Ktor server serialization docs](https://ktor.io/docs/server-serialization.html)

### Dependencies (Gradle artifact names)
```
io.ktor:ktor-server-core:3.4.1
io.ktor:ktor-server-cio:3.4.1
io.ktor:ktor-server-content-negotiation:3.4.1
io.ktor:ktor-serialization-jackson:3.4.1
```

### Decision: No ktor-server-test-host
Chose to NOT use `ktor-server-test-host` / `testApplication` because:
1. We need to test the real port binding and port file workflow
2. We already have OkHttp as a test HTTP client
3. Ktor test host creates an in-memory transport that skips real TCP -- defeats our testing purpose
Source: [Ktor server testing docs](https://ktor.io/docs/server-testing.html)

### Jackson Compatibility
- Project already has `jackson-databind:2.17.2` and `jackson-module-kotlin:2.17.2`
- Ktor 3.4.1 `ktor-serialization-jackson` also uses Jackson 2.17.x
- No version conflict expected. Gradle will resolve to the higher version if there is a minor diff.

## Codebase Pattern Observations

### Interface + Impl pattern (from TmuxCommunicator.kt, DirectLLM.kt)
- Interface in its own file or shared file with impl
- Implementation next to interface (same file or same package)
- Companion `.standard()` factory on interface (see Initializer.kt)
- Constructor injection of `OutFactory` + collaborators

### Out logging (from TmuxCommunicatorImpl.kt)
```kotlin
private val out = outFactory.getOutForClass(KtorHarnessServer::class)
out.info("message_in_snake_case", Val(value, ValType.SEMANTIC_TYPE))
```

### AsgardCloseable (from AppDependencies)
- Just a `suspend fun close()` method
- Used with `.use{}` at call sites

### Test patterns (from GLMHighestTierApiTest.kt, GitBranchManagerIntegTest.kt)
- `AsgardDescribeSpec` base class
- `outFactory` available as inherited property
- `withFixture` or `try/finally` cleanup pattern
- BDD: `describe("GIVEN...") { describe("WHEN...") { it("THEN...") } }`
- One assert per `it` block

### Build file pattern
- Dependencies inline (not version catalog) for non-core deps
- Version catalog only has guava and kotest currently
- Ktor deps should follow inline pattern to match existing style

## Risk Assessment

### Low Risk
- Ktor CIO is battle-tested, well-documented
- Port 0 binding is standard OS behavior
- Jackson integration is straightforward
- Test approach (real HTTP to localhost) is reliable

### Medium Risk
- Jackson Kotlin module registration: must be explicit in Ktor's `jackson {}` block. If missed, data class deserialization will fail with "no default constructor" errors.
- Port resolution timing: `resolvedConnectors()` must be called AFTER `start(wait=false)` completes. The Ktor API handles this correctly as long as start is awaited.

### Mitigated
- Port file directory creation: using `Files.createDirectories()` which is idempotent
- Cleanup on test failure: `try/finally` pattern ensures server shutdown and temp dir cleanup
