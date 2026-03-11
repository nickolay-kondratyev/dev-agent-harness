<deep_memory_pointers>
## DEEP_MEMORY- READ AS NEEDED (keep contents updated)

| File | Description |
|------|-------------|
| $(git.repo_root)/ai_input/memory/deep/dont_log_and_throw.md | Do NOT log and throw. Let exceptions bubble up, log at the top-most layer only. |
| $(git.repo_root)/ai_input/memory/deep/favor_functional_style.md | Prefer functional collection operations (map, filter, zip, takeWhile) over manual loops with index tracking. |
| $(git.repo_root)/ai_input/memory/deep/in_tests__fail_hard_never_mask.md | Tests must fail explicitly. No silent fallbacks, no conditional skipping of individual tests. Only entire test classes may be toggled. |
| $(git.repo_root)/ai_input/memory/deep/in_tests__one_assert_per_test.md | Structure tests with separate `it` blocks for each assertion. Use describe to group. Self-documenting. |
| $(git.repo_root)/ai_input/memory/deep/integ_tests__use_glm_for_agent_spawning.md | Integration tests that spawn real Claude Code agents MUST use GLM (Z.AI) instead of Claude. Documents the env-var injection mechanism. |
| $(git.repo_root)/ai_input/memory/deep/out_logging_patterns.md | How to use Out structured logging. Covers Out interface, Val, ValType, and lazy debug patterns. |

</deep_memory_pointers>
