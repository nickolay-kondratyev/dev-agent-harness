# DispatcherProvider Implementation - Working Notes

## Plan

**Goal**: Fix S6310 - Avoid hardcoded dispatchers by creating DispatcherProvider interface and injecting it into 5 files.

**Steps**:
1. [x] Create `app/src/main/kotlin/com/glassthought/shepherd/core/infra/DispatcherProvider.kt`
2. [x] Fix TmuxCommandRunner.kt - add constructor param, replace Dispatchers.IO
3. [x] Fix RoleCatalogLoader.kt - add to RoleCatalogLoaderImpl constructor, update companion factory
4. [x] Fix ClaudeCodeAgentSessionIdResolver.kt - add to main constructor, pass to FilesystemGuidScanner
5. [x] Fix ContextForAgentProviderImpl.kt - add constructor param, update companion factory
6. [x] Fix TicketParser.kt - add to TicketParserImpl constructor, update companion factory
7. [x] Run tests to verify all pass

## Status: COMPLETE

## Test Result
BUILD SUCCESSFUL - all tests passed.

## Key Decisions
- Used `fun interface DispatcherProvider` (SAM) - allows lambda syntax for test mocks
- Used default parameter `= DispatcherProvider.standard()` in all constructors - zero call site changes needed
- `FilesystemGuidScanner` is a private class inside ClaudeCodeAgentSessionIdResolver.kt - both the inner class AND the outer class got `dispatcherProvider` added with proper passing through
- `ContextForAgentProvider.standard()` factory did NOT need updating - default parameter handles it
