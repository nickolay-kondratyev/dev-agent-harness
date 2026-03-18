# TOP_LEVEL_AGENT: Fix Sonar Issues

## Ticket
ID: nid_lqo6g9lo6lvnrx6tmfr9q9ucj_E
Title: Fix sonar issues in existing code

## Strategy
Split 21 sonar issues into 3 serial sub-agents by issue type:

### Agent 1: Simple Fixes (S6532, S1192, S6514, S1172, S1135)
- S6532: Replace if with check() - ProcessResult.kt, EnvironmentValidator.kt (2 occurrences)
- S1192: Extract constants - TmuxCommunicator.kt ("send-keys"), ContextForAgentProviderImpl.kt ("\n\n---\n\n")
- S6514: Use `by` delegation - ShepherdContext.kt
- S1172: Remove unused `args` param - AppMain.kt
- S1135: TODO comment - AppMain.kt (INFO severity - skip, it's intentional WIP)

### Agent 2: Functional Interfaces (S6517)
- 8 interfaces need `fun` keyword added
- Files: ContextForAgentProvider.kt, EnvironmentValidator.kt, ContextInitializer.kt, AgentStarter.kt, AgentSessionIdResolver.kt, ClaudeCodeAgentSessionIdResolver.kt (inner GuidScanner), RoleCatalogLoader.kt, TicketParser.kt

### Agent 3: Hardcoded Dispatchers (S6310)
- 5 usages of Dispatchers.IO need DispatcherProvider injection
- Files: TmuxCommandRunner.kt, RoleCatalogLoader.kt, ClaudeCodeAgentSessionIdResolver.kt, ContextForAgentProviderImpl.kt, TicketParser.kt
- Requires creating DispatcherProvider interface + default impl

## Progress
- [x] EXPLORATION complete
- [ ] Agent 1: Simple fixes
- [ ] Agent 2: Functional interfaces
- [ ] Agent 3: Dispatcher provider
- [ ] Run ./run_sonar.sh to verify
- [ ] Close ticket
