---
id: nid_61on3cjpjxkn9rltnp0syo6ya_E
title: "add Environment interface"
status: in_progress
deps: []
links: []
created_iso: 2026-03-10T16:47:37Z
status_updated_iso: 2026-03-10T16:50:00Z
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



