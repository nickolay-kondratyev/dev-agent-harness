<deep_memory_pointers>
## DEEP_MEMORY- READ AS NEEDED (keep contents updated)

| File | Description |
|------|-------------|
| ${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/ai_input/memory/deep/dont_log_and_throw.md | Do NOT log and throw. Let exceptions bubble up, log at the top-most layer only. |
| ${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/ai_input/memory/deep/favor_functional_style.md | Prefer functional collection operations (map, filter, zip, takeWhile) over manual loops with index tracking. |
| ${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/ai_input/memory/deep/in_tests__fail_hard_never_mask.md | Tests must fail explicitly. No silent fallbacks, no conditional skipping of individual tests. Only entire test classes may be toggled. |
| ${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/ai_input/memory/deep/in_tests__one_assert_per_test.md | Structure tests with separate `it` blocks for each assertion. Use describe to group. Self-documenting. |
| ${GIT_REPOS}/nickolay-kondratyev_dev-agent-harness/ai_input/memory/deep/out_logging_patterns.md | How to use Out structured logging. Covers Out interface, Val, ValType, and lazy debug patterns. |

</deep_memory_pointers>
