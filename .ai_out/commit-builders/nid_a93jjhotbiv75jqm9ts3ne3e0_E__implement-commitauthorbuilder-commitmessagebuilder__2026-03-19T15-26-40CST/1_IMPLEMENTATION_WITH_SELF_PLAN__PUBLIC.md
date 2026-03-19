# CommitAuthorBuilder & CommitMessageBuilder — Implementation Complete

## What was done

Implemented two stateless utility objects for building git commit metadata, following the existing `BranchNameBuilder` pattern.

### CommitMessageBuilder
- Builds commit messages in format: `[shepherd] {part_name}/{sub_part_name} — {result} (iteration {N}/{max})`
- Iteration suffix included only when `hasReviewer=true`
- Input validation via `require()` for blank strings and iteration numbers

### CommitAuthorBuilder
- Builds commit author names in format: `{AGENT_CODE}_{model}_WITH-{hostUsername}`
- Maps `AgentType.CLAUDE_CODE` to `"CC"` and `AgentType.PI` to `"PI"`
- Uses exhaustive `when` on enum (no `else` branch)
- Input validation via `require()` for blank strings

## Files created

| File | Description |
|------|-------------|
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/CommitMessageBuilder.kt` | Stateless object for commit message formatting |
| `app/src/main/kotlin/com/glassthought/shepherd/core/supporting/git/CommitAuthorBuilder.kt` | Stateless object for commit author name formatting |
| `app/src/test/kotlin/com/glassthought/shepherd/core/git/CommitMessageBuilderTest.kt` | BDD tests covering with/without reviewer, various results, validation |
| `app/src/test/kotlin/com/glassthought/shepherd/core/git/CommitAuthorBuilderTest.kt` | BDD tests covering both agent types, different models/usernames, validation |

## Tests

All tests pass (`./gradlew :app:test` — BUILD SUCCESSFUL).

Test coverage:
- CommitMessageBuilder: 11 test cases (with reviewer, without reviewer, planning phase, validation errors)
- CommitAuthorBuilder: 6 test cases (CLAUDE_CODE, PI, different models/usernames, validation errors)
