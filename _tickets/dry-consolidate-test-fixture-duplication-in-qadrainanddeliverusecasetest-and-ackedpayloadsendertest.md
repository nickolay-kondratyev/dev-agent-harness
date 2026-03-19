---
closed_iso: 2026-03-19T19:46:08Z
id: nid_mz3z9d5obd79qbmrr6fr0jluo_E
title: "DRY: Consolidate test fixture duplication in QaDrainAndDeliverUseCaseTest and AckedPayloadSenderTest"
status: closed
deps: []
links: []
created_iso: 2026-03-19T19:18:14Z
status_updated_iso: 2026-03-19T19:46:08Z
type: chore
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [dry, test-quality]
---

Pre-existing duplication: QaDrainAndDeliverUseCaseTest.kt and AckedPayloadSenderTest.kt have private copies of noOpCommunicator, noOpExistsChecker, createTestTmuxAgentSession(), createTestSessionEntry() that duplicate the shared SessionTestFixtures.kt.

Files to update:
- app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt
- app/src/test/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSenderTest.kt

Shared fixture:
- app/src/test/kotlin/com/glassthought/shepherd/core/session/SessionTestFixtures.kt

ShepherdServerTest.kt already correctly reuses the shared fixtures.

