---
closed_iso: 2026-03-10T17:11:23Z
status: closed
id: nid_4ibk65jxp1o84i7dufi3bw2fd_E
title: AppMain.kt calls InitializerImpl() directly instead of Initializer.standard()
created_iso: 2026-03-10T17:01:43Z
status_updated_iso: 2026-03-10T17:11:23Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

AppMain.kt line 23 calls `InitializerImpl().initialize()` directly, bypassing the `Initializer` interface factory.

File: app/src/main/kotlin/com/glassthought/shepherd/cli/AppMain.kt
Line: ~23

Found during review of ticket nid_61on3cjpjxkn9rltnp0syo6ya_E (add Environment interface).

## Resolution

**Fixed** - Changed `AppMain.kt` to use `Initializer.standard().initialize()` instead of `InitializerImpl().initialize()`.

### Changes Made:
- Updated import from `InitializerImpl` to `Initializer`
- Changed call from `InitializerImpl().initialize()` to `Initializer.standard().initialize()`

This aligns `AppMain.kt` with `CallGLMApiSandboxMain.kt` which correctly uses the interface factory method, following good OOP practice (program to interface).

Commit: 2725540
