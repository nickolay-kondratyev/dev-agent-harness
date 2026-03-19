---
closed_iso: 2026-03-19T20:33:28Z
id: nid_o0ajw475drc46gblqd8s2prie_E
title: "Integrate coverage into sonar"
status: closed
deps: []
links: []
created_iso: 2026-03-19T20:29:31Z
status_updated_iso: 2026-03-19T20:33:28Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


Right now we have $(git.repo_root)/coverage.sh
Which creates report at ./.out/coverage.xml
Lets modify so that instead of putting coverage into coverage.xml it will be upload results into sonar
So that when we run `$(git.repo_root)/run_sonar.sh` we also get coverage metrics


## Notes

**2026-03-19T20:33:34Z**

Completed. Changes made:
1. build.gradle.kts: added sonar.coverage.jacoco.xmlReportPaths pointing to ${rootDir}/.out/coverage.xml (Kover produces JaCoCo-compatible XML)
2. run_sonar.sh: added koverXmlReport step before sonar scan so coverage is always fresh
3. coverage.sh: updated echo to clarify the XML feeds into run_sonar.sh
