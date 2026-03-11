# EXPLORATION: Wire up TmuxAgentSession Spawn Flow

## Existing Infrastructure

### Tmux Layer (all in `com.glassthought.chainsaw.core.tmux`)
- **TmuxSessionManager**: `createSession(name, command): TmuxSession`, `killSession(session)` — deps: TmuxCommandRunner, TmuxCommunicator, OutFactory
- **TmuxSession** (ap.7sZveqPcid5z1ntmLs27UqN6.E): name: TmuxSessionName, `sendKeys()`, `sendRawKeys()`, `exists()`
- **TmuxCommunicator** (interface + TmuxCommunicatorImpl): sends literal text + Enter to sessions
- **TmuxCommandRunner**: executes tmux commands via ProcessBuilder on Dispatchers.IO

### Wingman / AgentSessionIdResolver (`com.glassthought.chainsaw.core.wingman`)
- **AgentSessionIdResolver** (interface, ap.D3ICqiFdFFgbFIPLMTYdoyss.E): `suspend fun resolveSessionId(guid: HandshakeGuid): ResumableAgentSessionId`
- **ClaudeCodeAgentSessionIdResolver**: Uses GuidScanner to poll JSONL files for GUID match, extracts session ID from filename. Has pollInterval + timeout.
- **HandshakeGuid**: `@JvmInline value class HandshakeGuid(val value: String)`
- **ResumableAgentSessionId**: `data class(agentType: AgentType, sessionId: String)`
- **GuidScanner**: interface injected into the resolver (FilesystemGuidScanner walks dirs for JSONL)

### Environment / Initializer
- **Environment** (sealed interface): `val isTest: Boolean`; impls: ProductionEnvironment (false), TestEnvironment (true)
- **Initializer** → `AppDependencies`: outFactory, tmuxCommandRunner, tmuxCommunicator, tmuxSessionManager, glmDirectLLM
- TODO at ap.ifrXkqXjkvAajrA4QCy7V.E: use environment.isTest for test doubles

### Agent Type
- **AgentType** enum: `CLAUDE_CODE`, `PI`

### Server Layer
- AgentRequestHandler interface: onDone, onQuestion, onFailed, onStatus
- HarnessServer with Ktor CIO

## What DOES NOT Exist (needs creation)

1. **PhaseType** — enum/sealed for phase identification
2. **StartAgentRequest** — input to spawn use case (contains phaseType)
3. **TmuxAgentSession** — interface pairing TmuxSession + ResumableAgentSessionId
4. **AgentStarter** — interface: builds tmux start command
5. **TmuxStartCommand** — data class wrapping command components
6. **AgentTypeChooser** — interface: chooses AgentType from StartAgentRequest
7. **AgentStarterBundle** — data class: AgentStarter + AgentSessionIdResolver
8. **AgentStarterBundleFactory** — interface: creates bundles for (AgentType, PhaseType)
9. **SpawnTmuxAgentSessionUseCase** — orchestrates full spawn flow
10. **ResumeTmuxAgentSessionUseCase** — resumes from saved session ID
11. **ClaudeCodeAgentStarter** — builds Claude Code command with phase-specific config

## Integration Test Patterns

- Gate with `isIntegTestEnabled()` from `app/src/test/kotlin/org/example/integTestSupport.kt`
- Tests extend `AsgardDescribeSpec`, annotate `@OptIn(ExperimentalKotest::class)`
- BDD: GIVEN/WHEN/THEN in describe/it blocks
- Cleanup in `afterEach` blocks
- Existing tmux integ tests: TmuxSessionManagerIntegTest, TmuxCommunicatorIntegTest
- Wingman unit test uses fake GuidScanner injection

## Package Structure

Main: `app/src/main/kotlin/com/glassthought/chainsaw/core/`
- data/, directLLMApi/, filestructure/, git/, initializer/, processRunner/, rolecatalog/, server/, ticket/, tmux/, wingman/, workflow/

## Key File Paths
- TmuxSessionManager: `app/src/main/kotlin/com/glassthought/chainsaw/core/tmux/TmuxSessionManager.kt`
- AgentSessionIdResolver: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/AgentSessionIdResolver.kt`
- ClaudeCodeAgentSessionIdResolver: `app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/impl/ClaudeCodeAgentSessionIdResolver.kt`
- Environment: `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt`
- Initializer: `app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt`
- AgentType: `app/src/main/kotlin/com/glassthought/chainsaw/core/data/AgentType.kt`
- integTestSupport: `app/src/test/kotlin/org/example/integTestSupport.kt`
