---
id: nid_azwnh5dk5rdhgnd8653hdf6rv_E
title: "Migrate PlanConversionException to extend AsgardBaseException"
status: open
deps: []
links: []
created_iso: 2026-03-19T14:46:15Z
status_updated_iso: 2026-03-19T14:46:15Z
type: chore
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [tech-debt]
---

PlanConversionException currently extends RuntimeException because AsgardBaseException is not available in the current asgardCore 1.0.0 jar.

When AsgardBaseException becomes available, update:
- File: app/src/main/kotlin/com/glassthought/shepherd/core/state/PlanConversionException.kt
- Change: `RuntimeException` -> `AsgardBaseException`

Per CLAUDE.md Kotlin standards: "Extend AsgardBaseException hierarchy for structured exceptions."

