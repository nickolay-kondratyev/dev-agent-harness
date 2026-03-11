---
id: nid_g3z2de5zpq5dz608l9c651tam_E
title: "Fix resource leak in CallGLMApiSandboxMain - AppDependencies not closed"
status: open
deps: []
links: []
created_iso: 2026-03-11T14:49:30Z
status_updated_iso: 2026-03-11T14:49:30Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

In app/src/main/kotlin/com/glassthought/chainsaw/cli/sandbox/CallGLMApiSandboxMain.kt line 8, the AppDependencies instance is discarded without calling close():

```kotlin
val llm = Initializer.standard().initialize(outFactory = SimpleConsoleOutFactory.standard()).glmDirectLLM
```

Fix should use the .use{} pattern consistent with AppMain.kt:
```kotlin
Initializer.standard()
    .initialize(outFactory = SimpleConsoleOutFactory.standard())
    .use { deps ->
        deps.glmDirectLLM.call(...).also { ... }
    }
```

This is low-severity (JVM exit cleans up for a sandbox main) but is inconsistent with the .use{} pattern used elsewhere.

