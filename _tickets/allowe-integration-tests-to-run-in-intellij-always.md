---
id: nid_z2a3qlj5wpuhp5mbr6x4p5ff3_E
title: "Allowe integration tests to run in intellij always"
status: in_progress
deps: []
links: []
created_iso: 2026-03-11T01:51:35Z
status_updated_iso: 2026-03-11T01:53:19Z
type: task
priority: 3
assignee: nickolaykondratyev
---


Right now requires a property:
```kt file=[$(git.repo_root)/app/src/test/kotlin/org/example/integTestSupport.kt] Lines=[9-10]
fun isIntegTestEnabled(): Boolean = System.getProperty("runIntegTests") == "true"
```

What we would like is to be able to allow integration test to run in intellij without setting this property. 

Hence we will have something like IsRunningInIntellij() helper method that checks we are running in intellij and if so, allow integration tests to run without the property.

#no-review