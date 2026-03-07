---
closed_iso: 2026-03-07T22:59:51Z
id: nid_zdmfnpevga8p1im90xmk2g9bm_E
title: "Decouple from THORG_ROOT env variable"
status: closed
deps: []
links: []
created_iso: 2026-03-07T15:43:13Z
status_updated_iso: 2026-03-07T22:59:51Z
type: task
priority: 3
assignee: nickolaykondratyev
---

See memory at ref.ap.MKHNCkA2bpT63NAvjCnvbvsb.E

Right now THORG_ROOT is typically set to something that is not a $PWD/submodules/thorg-root.
And it will probably work most of the time because thorg root is valid but it could cause 
very confusing issues if we are setting  


