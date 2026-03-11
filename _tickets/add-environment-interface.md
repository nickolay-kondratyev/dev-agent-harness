---
closed_iso: 2026-03-10T17:01:50Z
id: nid_61on3cjpjxkn9rltnp0syo6ya_E
title: "add Environment interface"
status: closed
deps: []
links: []
created_iso: 2026-03-10T16:47:37Z
status_updated_iso: 2026-03-10T17:01:50Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Let's add `Environment` interface.

Let's add it under `./app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data`

It will start of with a `val` value of `isTest` which will be `false` in prod and `true` in tests.

Let's have this method accept environment and default to production environment setup: 

```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/Initializer.kt] Lines=[58-58]
override suspend fun initialize()
```




## Notes

**2026-03-10T17:01:50Z**

Resolved: Created sealed interface Environment with val isTest: Boolean in app/src/main/kotlin/com/glassthought/chainsaw/core/initializer/data/Environment.kt. ProductionEnvironment (isTest=false) and TestEnvironment (isTest=true) as internal classes. Updated Initializer.initialize() to accept environment: Environment = Environment.production(). BDD unit tests added. All tests pass.
