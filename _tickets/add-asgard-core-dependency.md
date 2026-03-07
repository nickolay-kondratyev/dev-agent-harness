---
closed_iso: 2026-03-07T15:04:26Z
id: nid_rjzn42oj3tzbz69wjgu9364cx_E
title: "Add asgard core dependency"
status: closed
deps: []
links: []
created_iso: 2026-03-07T14:10:51Z
status_updated_iso: 2026-03-07T15:04:26Z
type: task
priority: 3
assignee: nickolaykondratyev
---

We have submodule and within this submodule there is asgard core library that we want to take dependency on in this library.

/submodules/thorg-root/source/libraries/kotlin-mp/asgardCore

Take dependency on this library, Use Shell Runner to call 'echo' from the main App.

## Notes

**2026-03-07T15:04:36Z**

Resolved: Added asgardCore via kotlin-mp composite build. ProcessRunner calls echo from main(). OutFactory closed with AsgardCloseable.use. THORG_ROOT documented in CLAUDE.md.
