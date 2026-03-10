---
closed_iso: 2026-03-10T20:45:28Z
id: nid_v1goe46cdkcti7ac1zg6r1jwm_E
title: "gradle runner"
status: closed
deps: []
links: []
created_iso: 2026-03-10T18:24:31Z
status_updated_iso: 2026-03-10T20:45:28Z
type: task
priority: 3
assignee: nickolaykondratyev
---


I want to have `./gradle_run.sh` shell script. which will be something like

```
#!/usr/bin/env bash

main() {
  local chosen_path tasks_jsonl
  tasks_jsonl="$(./gradle_tasks_jsonl_cached.sh)"
  chosen_path=$(echo "${tasks_jsonl:?}" | jq .path -r | fzf)

  # -n: return true when value is not empty.
  if [[ -n "${chosen_path}" ]]; then
    if shell.is_command_defined history_add; then
      history_add ./gradle_run.sh
      history_add ./gradlew "${chosen_path:?}"
    fi

    eai2 ./gradlew "${chosen_path:?}"
  fi
}
```

```
m:fedora d:kotlin-mp b:master mirror-2 ○ ❯cat gradle_tasks_jsonl_cached.sh
#!/usr/bin/env bash

_gradle_tasks_jsonl() {
  ./gradlew --quiet --console=plain tasksJson | jq .[] -c
}
export -f _gradle_tasks_jsonl

main() {
  memoize_by_pwd _gradle_tasks_jsonl
}
```


This script will call into gradle to gather the tasks like the following:
```
tasks.register("tasksJson") {
  doLast {
    val taskList = rootProject.allprojects.flatMap { proj ->
      proj.tasks.map { task ->
        mapOf(
          "name" to task.name,
          "path" to task.path,
          "project" to proj.path,
          "group" to (task.group ?: "other"),
          "description" to (task.description ?: "")
        )
      }
    }

    println(
      groovy.json.JsonOutput.prettyPrint(
        groovy.json.JsonOutput.toJson(taskList)
      )
    )
  }
}
```

IDEALLY though in our version we will NOT rely on `memoize_by_pwd` shell magic and rather have "tasksJson" perform proper gradle level caching such that if any tasks change it will update the cache and if not it will return the cached version. This way we can avoid having to rely on shell level caching which can be more brittle (does not know when to update).
## Notes

**2026-03-10T20:45:24Z**

Implemented: (1) tasksJson Gradle task in build.gradle.kts with proper UP-TO-DATE caching (inputs: all build files + settings.gradle.kts, output: build/tasks-json/tasks.json); (2) gradle_tasks_jsonl_cached.sh — runs tasksJson and streams output as JSONL; (3) gradle_run.sh — fzf picker that calls gradle_tasks_jsonl_cached.sh and runs the selected task.
