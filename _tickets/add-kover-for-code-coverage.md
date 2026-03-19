---
closed_iso: 2026-03-19T20:21:47Z
id: nid_z5d5bocyheir7mjgktfydigu5_E
title: "Add Kover for code coverage"
status: closed
deps: []
links: []
created_iso: 2026-03-19T20:07:59Z
status_updated_iso: 2026-03-19T20:21:47Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


Add Kover to measure code coverage.

The output should be a JSON report in ./.out/ directory.

It should execute as explicit task and should not be a dependency of other gradle tasks for now.

`coverage.sh` should be the shell wrapper to run this gradle coverage task. 
## Notes

**2026-03-19T20:21:49Z**

Completed: Added Kover 0.9.1 for code coverage. XML report (not JSON — Kover doesn't support JSON) outputs to .out/coverage.xml. Created coverage.sh wrapper. koverVerify disabled in check/build lifecycle to keep coverage as explicit opt-in.
