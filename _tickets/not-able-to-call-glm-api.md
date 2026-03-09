---
id: nid_wnu7f6eeozzarewg3uu1p5w0q_E
title: "Not able to call GLM api"
status: open
deps: []
links: []
created_iso: 2026-03-09T18:22:36Z
status_updated_iso: 2026-03-09T18:22:36Z
type: task
priority: 3
assignee: nickolaykondratyev
---


I tried to use this code snippet to test the API call

```kt file=[$(git.repo_root)/app/src/main/kotlin/org/example/sandbox/CallGLMApiSandboxMain.kt] Lines=[1-13]
package org.example.sandbox

import com.glassthought.directLLMApi.ChatRequest
import com.glassthought.initializer.Initializer

suspend fun main(args: Array<String>) {
  val llm = Initializer.standard().initialize().glmDirectLLM


  llm.call(ChatRequest("Say hello in Russian")).also { response ->
    println("LLM response: ${response.text}")
  }
}
```

But failed with
```
Caused by: java.lang.IllegalStateException: Direct LLM API returned non-2xx status. HTTP status=[429], body_snippet=[{"error":{"code":"1113","message":"Insufficient balance or no resource package. Please recharge."}}]
```

I have GLM code subscription that I thought should work with API calls but maybe I am missing something. Maybe the configuration is not correct to call the API? (I currently still have credits left for this week)

Look into this.