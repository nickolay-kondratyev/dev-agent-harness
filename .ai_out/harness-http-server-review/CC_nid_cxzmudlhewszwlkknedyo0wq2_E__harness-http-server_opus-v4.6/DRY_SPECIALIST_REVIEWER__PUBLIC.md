# DRY + Specialist Review: Harness HTTP Server

**Overall verdict: NOT_READY**

Two issues require attention before this is production-ready. One is a correctness gap
(missing TODO/stub annotation on `/agent/question`), and one is a knowledge duplication
that creates a maintenance trap (port file path duplicated across the language boundary).

---

## Findings

---

### CRITICAL — Port file path is duplicated across the Bash/Kotlin boundary

**Issue:**

The port file path `$HOME/.chainsaw_agent_harness/server/port.txt` is a hardcoded string
in two separate places that must stay in sync:

- `scripts/harness-cli-for-agent.sh` line 11:
  ```bash
  PORT_FILE="${HOME}/.chainsaw_agent_harness/server/port.txt"
  ```
- `app/src/main/kotlin/com/glassthought/chainsaw/core/server/PortFileManager.kt` line 34-37:
  ```kotlin
  val DEFAULT_PATH: Path = Path.of(
      System.getProperty("user.home"),
      ".chainsaw_agent_harness", "server", "port.txt"
  )
  ```

These are different languages and different processes. There is no mechanism that enforces
they stay in sync. If the path changes in one place, the contract between the server and
agents silently breaks: the server writes to a new location, the agent reads from the old
one, and it fails at runtime with a "port file not found" error.

**Why it matters:**

This is a textbook knowledge duplication at a critical contract boundary. The path is not
cosmetic — it is the sole discovery mechanism between the harness server and any agent
running in a TMUX session. A path change in one file with no corresponding change in the
other would cause every agent invocation to fail, and the error would not point at the
source of the problem.

**Recommendation:**

Add a comment in both files referencing the other, naming this as a paired contract that
must change together. Example at the top of `harness-cli-for-agent.sh`:

```bash
# PORT_FILE path must match PortFileManager.DEFAULT_PATH in the Kotlin harness.
# ref.ap.NAVMACFCbnE7L6Geutwyk.E (KtorHarnessServer)
PORT_FILE="${HOME}/.chainsaw_agent_harness/server/port.txt"
```

And in `PortFileManager.kt` KDoc:

```
 * Default path must match PORT_FILE in harness-cli-for-agent.sh
 * (ref.ap.8PB8nMd93D3jipEWhME5n.E).
```

The KDoc already references `ref.ap.8PB8nMd93D3jipEWhME5n.E` but does not call out that
the literal path values must be kept in sync. The shell script has no reciprocal reference
at all. Making the bidirectional dependency explicit is the minimum acceptable fix.

A stronger fix — generating the path in one canonical place and deriving the other — is not
feasible across the Bash/JVM boundary, so explicit cross-references are the right 80/20
solution.

---

### IMPORTANT — `/agent/question` endpoint has no stub comment; behavior contradicts future architecture

**Issue:**

The routing code in `HarnessServer.kt` handles `/agent/question` identically to all other
endpoints via `handleAgentRequest`:

```kotlin
post("/question") { handleAgentRequest<AgentQuestionRequest>("/agent/question") }
```

It logs the request and immediately returns `{"status": "ok"}`.

The class-level KDoc says "4 stub POST endpoints" which acknowledges stub status at the
class level. However:

1. There is no inline comment at the routing site explaining that this endpoint is intentionally
   non-blocking and that the answer-delivery mechanism (TMUX `send-keys`) is not yet wired.
2. The shell script help text says `"question"` calls "return immediately" and the answer is
   delivered via TMUX — so the non-blocking behavior is the V1 design. But the harness has
   no code to actually deliver the answer via TMUX. The response of 200 signals "received"
   but the agent will never receive a human answer.
3. A future implementor reading only the routing code has no signal that this endpoint has
   special semantics (suspend-until-human-answers) versus the other three.

**Why it matters:**

Without the inline stub annotation at the call site, a future engineer implementing the
question workflow has no breadcrumb pointing them to the routing location that must change.
More seriously, this endpoint currently lies to the agent: it returns 200 as if the question
was answered, when no human interaction occurs. An agent built around the documented contract
(call `question`, wait for TMUX answer) will proceed without an answer after getting 200.
In V1 with no agents actually using this, the risk is low — but as soon as agent integration
starts, this silent gap will produce hard-to-diagnose behavior.

**Recommendation:**

Add an inline comment at the routing site:

```kotlin
// STUB: question endpoint is not yet connected to human Q&A or TMUX answer delivery.
// Future: suspend coroutine here until human provides an answer, then deliver via TMUX send-keys.
// ref.ap.7sZveqPcid5z1ntmLs27UqN6.E
post("/question") { handleAgentRequest<AgentQuestionRequest>("/agent/question") }
```

This makes the intentional incompleteness explicit at the exact code location a future
implementor will land on.

---

### OPTIONAL — `PortFileManager.DEFAULT_PATH` is defined but never used in production code

**Issue:**

`PortFileManager.DEFAULT_PATH` is declared as a public companion constant but is not
referenced anywhere in production wiring (`Initializer.kt`, `AppMain.kt`, or any other
main-path class). It exists but has no caller yet.

**Why it matters:**

This is a minor observation, not a bug. The constant is correct and will be needed when the
server is wired into `InitializerImpl`. However, an unused public constant is a mild smell:
if the path changes and a developer searches for usages to find all sites to update, they
will find only the definition — no usages — and may conclude the constant is dead code.
Combined with the Bash/Kotlin duplication finding above, the path could silently drift.

**Recommendation:**

When wiring `KtorHarnessServer` into `InitializerImpl`, use `PortFileManager(PortFileManager.DEFAULT_PATH)`
and confirm the constant is referenced at least once in production code. No action needed
before that wiring is done, but this is a reminder that the constant's purpose only becomes
meaningful at wiring time.

---

## DRY Assessment Summary

| Candidate | Real Duplication? | Verdict |
|-----------|------------------|---------|
| Port file path across Bash/Kotlin | YES — same fact, two places, no sync mechanism | Needs cross-reference comments |
| `handleAgentRequest` inline function | NO — good abstraction, correctly DRYs up receive-log-respond | No action |
| 4 request data classes sharing `branch: String` | NO — each class represents different knowledge (different endpoint semantics) and will evolve independently | No action |

## Concurrency Assessment Summary

V1 is serial (one agent at a time). `engine` and `boundPort` are accessed only from
`start()` and `close()`, which are called from the harness's single coordination coroutine.
Ktor request handlers only access `out` (immutable) and `call` (handler-scoped) — they
never touch `engine` or `boundPort`. The theoretical `close()` vs `port()` race has no
real trigger in V1. No synchronization changes are warranted.

Stale port file: `writePort` uses `Files.writeString` which atomically overwrites.
No delete-before-write gap. A stale file from a crashed previous run is overwritten on
the next `start()`. Acceptable for V1.
