---
id: nid_o0ajw475drc46gblqd8s2prie_E
title: "Integrate coverage into sonar"
status: open
deps: []
links: []
created_iso: 2026-03-19T20:29:31Z
status_updated_iso: 2026-03-19T20:29:31Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


Right now we have $(git.repo_root)/coverage.sh
Which creates report at ./.out/coverage.xml
Lets modify so that instead of putting coverage into coverage.xml it will be upload results into sonar
So that when we run `$(git.repo_root)/run_sonar.sh` we also get coverage metrics

