---
id: nid_wsx7pnjsnmab6xe6va8m1zwgt_E
title: "split sonar script"
status: in_progress
deps: []
links: []
created_iso: 2026-03-18T23:28:57Z
status_updated_iso: 2026-03-18T23:30:00Z
type: task
priority: 3
assignee: nickolaykondratyev
---

Split the `$(git.repo_root)/run_sonar.sh` into running sonar script and creating the report. Put the report additoins under /home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-2/scripts/tools/sonar and invoke it from run_sonar.sh the running of sonar can remain in gradle to KISS.