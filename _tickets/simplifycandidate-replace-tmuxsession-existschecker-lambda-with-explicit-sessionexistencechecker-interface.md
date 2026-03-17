---
id: nid_ghx184m7b6s7w0e2hla3kq6gr_E
title: "SIMPLIFY_CANDIDATE: Replace TmuxSession existsChecker lambda with explicit SessionExistenceChecker interface"
status: in_progress
deps: []
links: []
created_iso: 2026-03-17T21:39:28Z
status_updated_iso: 2026-03-17T21:53:32Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [simplification, tmux, testability, interfaces]
---

TmuxSession (app/src/main/kotlin/com/glassthought/shepherd/core/agent/tmux/TmuxSession.kt) injects a session existence check as a raw lambda to break a circular dependency with TmuxSessionManager:

```kotlin
private val existsChecker: suspend () -> Boolean
```

This "clever" lambda approach has three problems:
1. The dependency is invisible — nothing in the type signature indicates what the lambda represents
2. Testability: tests must manually construct and inject anonymous lambdas instead of working with a mockable interface
3. Captures TmuxSessionManager state invisibly — if the manager is replaced, the captured lambda becomes stale

**Simplification:** Extract an explicit `SessionExistenceChecker` interface:

```kotlin
fun interface SessionExistenceChecker {
    suspend fun exists(sessionName: TmuxSessionName): Boolean
}
```

TmuxSessionManager implements it, TmuxSession receives it via constructor injection. The Kotlin `fun interface` SAM conversion means the call-site syntax is identical to the lambda form — zero migration cost for existing callers.

**Robustness improvement:** The interface makes the dependency visible in the type hierarchy, enables clean mocking in tests, and eliminates implicit capture-by-reference fragility. Aligns with the project standard (Kotlin standards doc: "Be classy and use interfaces").

