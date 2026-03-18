# DispatcherProvider - Changes Summary

## What Was Done

Fixed Sonar S6310 "Avoid hardcoded dispatchers" in 5 files by:
1. Creating a `DispatcherProvider` abstraction
2. Injecting it (with defaults) into all affected classes

## Files Modified

### New File
- `app/src/main/kotlin/com/glassthought/shepherd/core/infra/DispatcherProvider.kt`
  - `fun interface DispatcherProvider` with `io(): CoroutineDispatcher` method
  - `companion object { fun standard() }` returning `Dispatchers.IO`-backed instance
  - Anchor point: `ap.Dispatcher_Provider_Interface.E`

### Modified Files

1. `app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/util/TmuxCommandRunner.kt`
   - Added `dispatcherProvider: DispatcherProvider = DispatcherProvider.standard()` constructor param
   - Replaced `Dispatchers.IO` with `dispatcherProvider.io()`

2. `app/src/main/kotlin/com/glassthought/shepherd/core/agent/rolecatalog/RoleCatalogLoader.kt`
   - Added `dispatcherProvider: DispatcherProvider = DispatcherProvider.standard()` to `RoleCatalogLoaderImpl` constructor
   - Replaced `Dispatchers.IO` with `dispatcherProvider.io()`

3. `app/src/main/kotlin/com/glassthought/shepherd/core/agent/sessionresolver/impl/ClaudeCodeAgentSessionIdResolver.kt`
   - Added `dispatcherProvider: DispatcherProvider = DispatcherProvider.standard()` to `ClaudeCodeAgentSessionIdResolver` constructor
   - Added same parameter to private `FilesystemGuidScanner` inner class
   - Passed `dispatcherProvider` from outer class to `FilesystemGuidScanner` constructor
   - Replaced `Dispatchers.IO` with `dispatcherProvider.io()`

4. `app/src/main/kotlin/com/glassthought/shepherd/core/context/ContextForAgentProviderImpl.kt`
   - Added `dispatcherProvider: DispatcherProvider = DispatcherProvider.standard()` to `ContextForAgentProviderImpl` constructor
   - Replaced `Dispatchers.IO` with `dispatcherProvider.io()`

5. `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/ticket/TicketParser.kt`
   - Added `dispatcherProvider: DispatcherProvider = DispatcherProvider.standard()` to `TicketParserImpl` constructor
   - Replaced `Dispatchers.IO` with `dispatcherProvider.io()`

## Tests

BUILD SUCCESSFUL - all tests passed. No existing call sites were broken (all constructors use default parameter values).
