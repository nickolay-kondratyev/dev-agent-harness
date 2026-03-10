Use this tool proactively when you're about to start a non-trivial implementation task. Getting user sign-off on your approach before writing code prevents wasted effort and ensures alignment. This tool transitions you into plan mode where you can explore the codebase and design an implementation approach for user approval.

## When to Use This Tool

## When to Use This Tool

Use EnterPlanMode when: multiple valid approaches exist, significant architectural decisions needed, large-scale changes across many files, unclear requirements needing exploration, or user input needed before starting.

## When NOT to Use This Tool

Only skip EnterPlanMode for simple tasks:
- Single-line or few-line fixes (typos, obvious bugs, small tweaks)
- Adding a single function with clear requirements
- Tasks where the user has given very specific, detailed instructions
- Pure research/exploration tasks (use the Agent tool with explore agent instead)

## What Happens in Plan Mode

Explore codebase, design approach, present plan for approval. Use AskUserQuestion to clarify, ExitPlanMode when ready.

## Examples

GOOD: "Add user authentication" - requires architectural decisions (session vs JWT, middleware)
BAD: "Fix the typo in README" - straightforward, no planning needed

## Important Notes

- This tool REQUIRES user approval - they must consent to entering plan mode
- If unsure whether to use it, err on the side of planning - it's better to get alignment upfront than to redo work
- Users appreciate being consulted before significant changes are made to their codebase
