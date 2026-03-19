---
closed_iso: 2026-03-19T22:42:31Z
id: nid_axc4pcrl2vnqo2vebffi2pxdd_E
title: "Figure out a way to easily spot which files are lacking sufficient code coverage"
status: closed
deps: []
links: []
created_iso: 2026-03-19T22:38:37Z
status_updated_iso: 2026-03-19T22:42:31Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


Figure out a way to spot which files are lacking code coverage that have lines/branches of logic uncovered. And propose a solution in this ticket.

We currently have Kover.

## Resolution

Created two files:

### `coverage_report.py` — Python script that parses Kover XML
- Parses `.out/coverage.xml` (Kover's XML output)
- Extracts per-file LINE and BRANCH coverage counters
- Sorts files by line coverage ascending (**worst coverage first**)
- Color-coded output: red (<threshold), yellow (partial), green (100%)
- Displays summary: overall coverage %, count of files below threshold
- Configurable threshold via `--threshold N` (default: 80%)

### `coverage_report.sh` — Shell wrapper
- Runs `coverage.sh` (Gradle Kover task) then displays the report
- `--skip-run` flag to show report from existing XML without re-running Gradle
- `--threshold N` passes through to the Python script

### Usage
```bash
# Full: run tests + generate coverage + show report
./coverage_report.sh

# Quick: show report from last coverage run
./coverage_report.sh --skip-run

# Custom threshold
./coverage_report.sh --threshold 60
```

### Sample output
```
Code Coverage Report (threshold: 80%)
Overall line coverage:  93.4%  (3013/3226 lines)
Files below 80%: 12 / 118

── Files BELOW 80% threshold ──
File                                     Lines    Line%  Branch%   Missed
──────────────────────────────────────────────────────────────────────────
com.glassthought.shepherd.cli/AppMain.kt 0/   3    0.0%   100.0%       3
...
```