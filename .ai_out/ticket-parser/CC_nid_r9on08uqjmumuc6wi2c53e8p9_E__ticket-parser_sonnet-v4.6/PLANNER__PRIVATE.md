# Planner Private State

## Status
Plan written. Ready for implementation.

## Key Observations

### Codebase Patterns Confirmed
- Interface + Impl naming convention: `TmuxCommunicator` + `TmuxCommunicatorImpl`, `DirectLLM` + `GLMHighestTierApi`. For TicketParser: `TicketParser` interface + `TicketParserImpl`.
- `companion object { fun standard(...): TicketParser }` factory on interface — matches `Initializer.standard()` pattern.
- `@AnchorPoint("ap.XXX.E")` annotation on implementation class.
- `out = outFactory.getOutForClass(TicketParserImpl::class)` — consistent with `TmuxCommunicatorImpl`.
- Tests live under `com.glassthought.chainsaw.*` namespace (NOT `org.example` — that's old/legacy).
- `outFactory` is inherited from `AsgardDescribeSpec` — confirmed in `GLMHighestTierApiTest`.
- `shouldThrow<IllegalArgumentException>` from `io.kotest.assertions.throwables`.

### Dependency to Add
```kotlin
implementation("org.yaml:snakeyaml:2.2")
```

### Test Package Note
Existing tests show two namespaces:
- Legacy: `org.example` (old tests — TmuxCommunicatorIntegTest, AppTest, etc.)
- Current: `com.glassthought.*` (new tests — GLMHighestTierApiTest, AppDependenciesCloseTest)

New tests MUST use `com.glassthought.chainsaw.core.ticket` — matching the main source package.

### No integTestSupport import needed
The TicketParser tests are pure unit tests (no TMUX, no network). No `.config(isIntegTestEnabled())` gating needed.

### YamlFrontmatterParser — Pure Object, No Suspend
Since it only transforms strings (no I/O), `parse()` is a regular (non-suspend) function. This keeps it maximally reusable (callable from both suspend and non-suspend contexts).

### TicketParserImpl — File Read Pattern
Use `withContext(Dispatchers.IO) { path.readText() }`. This is the standard Kotlin coroutines pattern for blocking file I/O. `path.readText()` is a Kotlin stdlib extension on `java.nio.file.Path`.

### additionalFields Filtering
Must exclude `id`, `title`, `status` from `additionalFields` since those are promoted to typed fields.
Pattern: `yamlFields.filterKeys { it !in setOf("id", "title", "status") }`

## AP Cross-linking Steps (Phase 8)
- Run `anchor_point.create` shell command to get new UUID
- Add the AP in `_tickets/clarify-high-level-approach-on-how-we-are-going-to-work-with-the-agent.md` below `## CLI Entry Point`
- Add `ref.ap.NEWUUID.E` in the KDoc of `TicketParser` interface
- Add `@AnchorPoint("ap.NEWUUID.E")` on `TicketParserImpl` class (consistent with `TmuxCommunicatorImpl`)

## Estimated Complexity
LOW — straightforward parsing. The only non-trivial piece is the delimiter detection in `YamlFrontmatterParser`, which is handled cleanly with a line-based algorithm.

## Risk Assessment
- snakeyaml is a mature, stable library. No integration risk.
- File reading with `path.readText()` is standard Kotlin. No risk.
- All design decisions already made. Implementation is mechanical.
