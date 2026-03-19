---
id: nid_7xzhkw4pw5sc5hqh80cvsotdc_E
title: "Wire AiOutputStructure into TicketShepherdCreator — .ai_out/ setup during ticket initialization"
status: open
deps: [nid_fjod8du6esers3ajur2h7tvgx_E, nid_9kic96nh6mb8r5legcsvt46uy_E, nid_o4gj7swdejriooj5bex3b34vf_E]
links: [nid_zseecydaikj0f2i2l14nwcfax_E, nid_o5azwgdl76nnofttpt7ljgkua_E, nid_itogi6ji82dbhb0k3zzt6v8qp_E]
created_iso: 2026-03-18T20:45:06Z
status_updated_iso: 2026-03-19T18:26:16Z
type: task
priority: 2
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
tags: [ai-out, wiring]
---

Wire AiOutputStructure into TicketShepherdCreator so that the .ai_out/ directory structure is created as part of ticket setup.

## What to Build

### In TicketShepherdCreator (ref.ap.cJbeC4udcM3J8UFoWXfGh.E)
1. After branch creation (git checkout -b), construct `AiOutputStructure(repoRoot, branchName)`.
2. Call `ensureStructure()` with the workflow parts configuration.
3. Pass `AiOutputStructure` instance to `ContextForAgentProvider` constructor so it can use path resolution instead of computing paths internally.
4. Pass `AiOutputStructure` instance to `TicketShepherd` so it can reference paths during orchestration.

### Integration Points
- `ContextForAgentProviderImpl` receives `AiOutputStructure` as constructor dependency — the sealed `AgentInstructionRequest` redesign (nid_8ts4qxw2wevxwep3yk2gvqwja_E, which is a dependency of this ticket) must complete first so we inject into the final form of the class.
- `currentStateJson()` path is needed by CurrentState flush logic (nid_o5azwgdl76nnofttpt7ljgkua_E).
- `planFlowJson()` and `planMd()` paths are needed by SetupPlanUseCase (ref.ap.VLjh11HdzC8ZOhNCDOr2g.E).

### Tests
- Unit test: after `TicketShepherdCreator` completes setup, verify the `.ai_out/${branch}/` directory tree exists on disk with all expected subdirectories (`harness_private/`, `shared/plan/`, planning sub-part dirs, execution part/sub-part dirs, `__feedback/{pending,addressed,rejected}/`).
- Unit test: verify `ContextForAgentProviderImpl` and `TicketShepherd` both receive the same `AiOutputStructure` instance (constructor injection verified).

### Spec References
- doc/schema/ai-out-directory.md, "Initial Creation" section (ref.ap.BXQlLDTec7cVVOrzXWfR7.E)
- doc/core/TicketShepherdCreator.md (ref.ap.cJbeC4udcM3J8UFoWXfGh.E) — "Sets up .ai_out/ directory structure for the branch"


## Notes

**2026-03-18T21:20:18Z**

Test clarity: The directory-existence verification belongs in ticket nid_fjod8du6esers3ajur2h7tvgx_E's scope (ensureStructure tests). This ticket's unit tests should focus on: (1) verifying AiOutputStructure is correctly injected as constructor dependency into ContextForAgentProviderImpl and TicketShepherd, (2) verifying ensureStructure() is called with correct parts list during setup. Read nid_8ts4qxw2wevxwep3yk2gvqwja_E to see finalized ContextForAgentProviderImpl constructor before adding aiOutputStructure as a parameter.

**2026-03-18T21:37:44Z**

Test clarification: Add an explicit test assertion that TicketShepherd receives the AiOutputStructure instance (constructor injection verified), in addition to the existing test spec for ContextForAgentProviderImpl injection. Both consumers should be verified.

**2026-03-18T21:51:42Z**

TicketShepherd spec reference: ref.ap.P3po8Obvcjw4IXsSUSU91.E (doc/core/TicketShepherd.md) — read this to understand where AiOutputStructure slots into TicketShepherd constructor/usage.

**2026-03-18T21:56:55Z**

Test scope clarification: The body mentions verifying directory tree exists on disk — IGNORE that, it belongs in nid_fjod8du6esers3ajur2h7tvgx_E. This ticket's tests focus on: (1) AiOutputStructure correctly injected into ContextForAgentProviderImpl and TicketShepherd, (2) ensureStructure() called with correct parts list. Also: SetupPlanUseCase wiring (planFlowJson/planMd paths) is OUT OF SCOPE for this ticket — SetupPlanUseCase has its own tickets. This ticket is purely about AiOutputStructure construction + injection + ensureStructure() call.

**2026-03-18T22:03:11Z**

Add test case from review: verify ContextForAgentProviderImpl uses the injected AiOutputStructure instance for PRIVATE.md path resolution (e.g., executionPrivateMd(partName, subPartName) / planningPrivateMd(subPartName)) rather than constructing the path internally. Guards against the injection being present but unused.

**2026-03-18T22:08:33Z**

IMPORTANT: ensureStructure() must be called with ALL parts from the parsed WorkflowDefinition (both planning and execution parts), not just the parts currently in CurrentState.parts. In with-planning workflows, CurrentState is initialized with only planning parts; the full part list for directory creation must come from the workflow JSON directly.

Also: dependency on nid_o4gj7swdejriooj5bex3b34vf_E added — ContextForAgentProviderImpl still navigates the old 9-directory feedback layout (feedbackDir/{status}/{severity}/). AiOutputStructure creates the new flat 3-directory layout (pending/addressed/rejected/ with severity in filename prefixes). Wiring must happen AFTER the feedback alignment ticket lands, or the provider will navigate wrong paths.

**2026-03-19T01:01:21Z**

Removed stale dependency on nid_8ts4qxw2wevxwep3yk2gvqwja_E (CODE_CONSISTENCY_FIX: ContextForAgentProvider redesign) — that work is closed/completed. The Integration Points section reference to nid_8ts4qxw2wevxwep3yk2gvqwja_E is now historical context only.
