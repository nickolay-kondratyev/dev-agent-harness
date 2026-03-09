# Planner Private Notes: Wingman Session ID Tracker

## Codebase Pattern References

### Interface + Impl in Same File
The codebase convention (observed in `TmuxCommunicator.kt`) is to put the interface and its default implementation in the **same file**. However, for Wingman, separate files make more sense because:
- The interface is generic (`Wingman`) while the impl is specific (`ClaudeCodeWingman`)
- Future agent types will have different implementations
- This matches the `DirectLLM` (interface) / `GLMHighestTierApi` (impl) split across files

### AnchorPoint Placement
- `@AnchorPoint` goes on the **implementation class**, not the interface
- Pattern from `TmuxCommunicator.kt`: interface is clean, impl has `@AnchorPoint`
- But the `ref.ap.XXX.E` goes in the interface's **KDoc** (to cross-reference the design doc section)

### Exception Pattern
- Use `IllegalStateException` (not custom exceptions) -- consistent with `GLMHighestTierApi`
- Include bracketed values in messages: `[${value}]` format (matching bash logging convention from CLAUDE.md)

### Logging ValType
- Explored codebase uses: `ValType.STRING_USER_AGNOSTIC`, `ValType.SHELL_COMMAND`, `ValType.JSON_SERVER_REQUEST`, `ValType.SERVER_RESPONSE_BODY`, `ValType.SERVER_URL_USER_AGNOSTIC`
- For GUID: `ValType.STRING_USER_AGNOSTIC` is appropriate
- For file paths: check if `ValType.FILE_PATH` exists; if not, use `STRING_USER_AGNOSTIC`
- For session ID: `ValType.STRING_USER_AGNOSTIC`

### Test Package
- Unit tests go under `com.glassthought.chainsaw.core.wingman` (matching the source package)
- This aligns with the test at `com.glassthought.directLLMApi.glm.GLMHighestTierApiTest`
- Tests extend `AsgardDescribeSpec` -- `outFactory` is inherited, do NOT construct manually

### Files.walk Stream Closing
Critical implementation detail: `Files.walk()` returns a `Stream<Path>` that MUST be closed. In Kotlin, use:
```kotlin
Files.walk(dir).use { stream ->
    stream.filter { ... }.toList()
}
```
The `.use {}` extension works on `Stream` because it implements `AutoCloseable`.

### Dispatchers.IO
The `GLMHighestTierApi` wraps blocking OkHttp calls in `withContext(Dispatchers.IO)`. The same pattern should be used for `Files.walk` + `readText` calls in `ClaudeCodeWingman`.

## Risk Assessment
- **LOW RISK**: This is a self-contained feature with no impact on existing code
- **NO new dependencies**: everything needed is already available
- **Straightforward testing**: temp directories provide perfect isolation
