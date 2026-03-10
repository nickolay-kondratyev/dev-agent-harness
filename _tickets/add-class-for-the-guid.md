---
id: nid_mkxn4qa9tqlvvm9zxq4es8cg2_E
title: "Add class for the guid"
status: open
deps: []
links: []
created_iso: 2026-03-10T20:03:24Z
status_updated_iso: 2026-03-10T20:03:24Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Refactor this to have a strong type of the GUID instead of string.

```kt file=[$(git.repo_root)/app/src/main/kotlin/com/glassthought/chainsaw/core/wingman/Wingman.kt] Lines=[22-23]
    suspend fun resolveSessionId(guid: String): String
```

We will want to use something like

import kotlin.jvm.JvmInline
@JvmInline
value class UserId(val id: String)  // Property can have any name

For the GUID that was sent to have strong typing and 