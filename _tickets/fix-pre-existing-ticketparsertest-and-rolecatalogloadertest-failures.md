---
id: nid_611rashdkhgxp74gwwkvpeo35_E
title: "Fix pre-existing TicketParserTest and RoleCatalogLoaderTest failures"
status: open
deps: []
links: []
created_iso: 2026-03-11T20:42:43Z
status_updated_iso: 2026-03-11T20:42:43Z
type: bug
priority: 2
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
tags: [server, cleanup, testing]
---

Pre-existing test failures discovered during server cleanup ticket.

Failing tests (NullPointerException at TicketParserTest.kt:18):
- TicketParserTest: All scenarios fail with NPE at line 18
- RoleCatalogLoaderTest: All 4 scenarios fail with initializationError

These failures existed before branch CC_nid_e0525c8z4eiu1ktey7yu868yv_E__remove-pre-spec-server-implementation-harnessserver-agentrequests-agentrequesthandler-for-clean-rebuild_sonnet-v4.6 was created.

Test files:
- app/src/test/kotlin/com/glassthought/ticketShepherd/core/ticket/TicketParserTest.kt
- app/src/test/kotlin/com/glassthought/ticketShepherd/core/rolecatalog/RoleCatalogLoaderTest.kt

Investigate and fix root cause per CLAUDE.md: start with failing test, fix, verify.

