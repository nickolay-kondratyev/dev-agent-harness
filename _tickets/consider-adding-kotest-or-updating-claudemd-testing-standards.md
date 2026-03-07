---
id: nid_lhli4opoz32dcswjky7hh4p6q_E
title: "Consider adding Kotest or updating CLAUDE.md testing standards"
status: open
deps: []
links: []
created_iso: 2026-03-07T19:31:18Z
status_updated_iso: 2026-03-07T19:31:18Z
type: chore
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [testing, docs]
---

CLAUDE.md specifies BDD with Kotest DescribeSpec, but the project uses JUnit 5 with @Test.
All existing tests (InteractiveProcessRunnerTest, TmuxSessionManagerTest, TmuxCommunicatorTest) use JUnit 5.

Options:
1. Add Kotest dependency and migrate tests to DescribeSpec
2. Update CLAUDE.md testing standards to reflect JUnit 5 usage

This is a documentation/consistency issue, not a functional bug.

