# Exploration: Wire InterruptHandler into TicketShepherdCreator

## Current State

- **TicketShepherdCreator**: Spec only (`doc/core/TicketShepherdCreator.md`, ref.ap.cJbeC4udcM3J8UFoWXfGh.E). No code implementation.
- **TicketShepherd**: Spec only (`doc/core/TicketShepherd.md`, ref.ap.P3po8Obvcjw4IXsSUSU91.E). No code class.
- **InterruptHandlerImpl** (ref.ap.yWFAwVrZdx1UTDqDJmDpe.E): Fully implemented with comprehensive tests.
- **AppMain.kt**: Has `TODO("CLI not yet implemented")` after EnvironmentValidator.

## Key Dependencies (all exist as code)

| Dependency | Interface | Production Impl | Location |
|---|---|---|---|
| Clock | `Clock` | `SystemClock` | `core/time/Clock.kt` |
| AllSessionsKiller | `AllSessionsKiller` | `TmuxAllSessionsKiller` | `core/agent/tmux/TmuxAllSessionsKiller.kt` |
| CurrentState | (data class) | `CurrentState` | `core/state/CurrentState.kt` |
| CurrentStatePersistence | `CurrentStatePersistence` | `CurrentStatePersistenceImpl` | `core/state/CurrentStatePersistence.kt` |
| ConsoleOutput | `ConsoleOutput` | `DefaultConsoleOutput` | `core/infra/ConsoleOutput.kt` |
| ProcessExiter | `ProcessExiter` | `DefaultProcessExiter` | `core/infra/ProcessExiter.kt` |

## Startup Sequence (from spec)

0. EnvironmentValidator.validate() ✅ implemented
1. ContextInitializer → ShepherdContext ✅ implemented
2. ShepherdServer startup — not yet implemented
3. **TicketShepherdCreator → TicketShepherd** — this ticket adds InterruptHandler wiring here
4. TicketShepherd.run() — not yet implemented
5. Cleanup — not yet implemented

## InterruptHandler.install() placement

Per ticket: "after CurrentState is initialized but before the main execution loop starts."
In startup sequence: inside step 3 (TicketShepherdCreator.create()), after CurrentState creation.

## TmuxAllSessionsKiller construction

Needs: `outFactory` + `tmuxCommandRunner` — both available from `ShepherdContext.infra.tmux.commandRunner` and `ShepherdContext.infra.outFactory`.

## CurrentStatePersistenceImpl construction

Needs: `AiOutputStructure` — created during TicketShepherdCreator's `.ai_out/` setup step.
