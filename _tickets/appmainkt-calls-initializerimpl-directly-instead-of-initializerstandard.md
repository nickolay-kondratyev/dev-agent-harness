---
id: nid_4ibk65jxp1o84i7dufi3bw2fd_E
title: "AppMain.kt calls InitializerImpl() directly instead of Initializer.standard()"
status: open
deps: []
links: []
created_iso: 2026-03-10T17:01:43Z
status_updated_iso: 2026-03-10T17:01:43Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

AppMain.kt line 23 calls `InitializerImpl().initialize()` directly, bypassing the `Initializer` interface factory.

It should use `Initializer.standard().initialize()` for consistency with CallGLMApiSandboxMain.kt and good OOP practice (program to interface).

File: app/src/main/kotlin/com/glassthought/chainsaw/cli/AppMain.kt
Line: ~23

Found during review of ticket nid_61on3cjpjxkn9rltnp0syo6ya_E (add Environment interface).

