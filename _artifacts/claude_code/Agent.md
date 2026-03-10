Launch agents for complex, multi-step tasks. Specify subagent_type parameter.

Available agent types:
- general-purpose: For complex searches, code exploration, and multi-step tasks. Use when unsure about finding the right match quickly. (Tools: *)
- statusline-setup: Use this agent to configure the user's Claude Code status line setting. (Tools: Read, Edit)
- Explore: Fast codebase exploration. Find files by pattern, search code, answer codebase questions. Specify thoroughness: "quick", "medium", or "very thorough". (Tools: All tools except Agent, ExitPlanMode, Edit, Write, NotebookEdit)
- Plan: For implementation planning. Returns step-by-step plans with critical files and trade-offs. (Tools: All tools except Agent, ExitPlanMode, Edit, Write, NotebookEdit)
- claude-code-guide: For questions about Claude Code, Agent SDK, or Claude API. Check for existing agent to resume before spawning new. (Tools: Glob, Grep, Read, WebFetch, WebSearch)
- SRP_FIXER: Use it when asked for [SRP_FIXER] role (Tools: All tools)
- ARCHITECTURE: Use it when asked for [ARCHITECTURE] role (Tools: All tools)
- THORG_DOC_NOTE_UPDATER: Use this agent when asked to use [THORG_DOC_NOTE_UPDATER] as sub-agent. (Tools: All tools)
- PRINCIPAL_UX_REVIEWER: Reviews UX design directions for completeness and handoff readiness (Tools: All tools)
- PLAN_REVIEWER: Use this agent when asked to use PLAN_REVIEWER as sub-agent for stages like DETAILED_PLAN_REVIEW. (Tools: All tools)
- user-testing-flow-validator: Test validation contract assertions through real user surface during mission validation. Used only within missions. (Tools: All tools)
- PLAYWRIGHT_REVIEW_WITH_SCREENSHOTS: Use this agent for behavioral QA testing via Playwright MCP. Acts as the last line of defense before shipping—validates new requirements are met and catches regressions through hands-on testing with screenshots. (Tools: All tools)
- scrutiny-feature-reviewer: Code review for a single feature during mission validation. Used only within missions. (Tools: All tools)
- IMPLEMENTATION_PLANNER: Use this agent when asked IMPLEMENTATION_PLANNER role as sub-agent. (Tools: All tools)
- worker: General-purpose worker droid for delegating tasks. Use for non-trivial tasks that benefit from parallel execution, such as code exploration, Q&A, research, analysis. (Tools: All tools)
- PARETO_COMPLEXITY_ANALYSIS: Evaluates if implementation complexity is justified by value delivered (Tools: All tools)
- IMPLEMENTATION_REVIEWER: Use this agent when asked to use IMPLEMENTATION_REVIEWER as sub-agent. (Tools: All tools)
- RESEARCH_REVIEW: Use this agent when asked to use RESEARCH_REVIEW as sub-agent. (Tools: All tools)
- DRY_REVIEWER: Use it when asked for DRY_REVIEWER role (Tools: All tools)
- DRY_FIXER: Use it when asked for [DRY_FIXER] role (Tools: All tools)
- RESEARCH: Use this agent when asked to use [RESEARCH] as sub-agent. (Tools: All tools)
- DRY_SRP_FIXER: Use it when asked for [DRY_SRP_FIXER] role (Tools: All tools)
- DOC_FIXER: Use it when asked for [DOC_FIXER] role (Tools: All tools)
- THORG_DOC_NOTE_REVIEW: Use this agent when asked to use [THORG_DOC_NOTE_REVIEW] as sub-agent. (Tools: All tools)
- ARCHITECTURE_REVIEWER: Use this agent when asked to use ARCHITECTURE_REVIEWER as sub-agent. (Tools: All tools)
- PLANNER: Use it when asked for [PLANNER] role. (Tools: All tools)
- IMPLEMENTATION_WITH_SELF_PLAN: Use this agent when asked to use IMPLEMENTATION_WITH_SELF_PLAN as sub-agent/role. (Tools: All tools)
- UX_UI_IMPLEMENTATION_WITH_SELF_PLAN: Use it when asked for [UX_UI_IMPLEMENTATION_WITH_SELF_PLAN] role, or need self-contained UI work. (Tools: All tools)
- IMPLEMENTATION: Use this agent when asked to use IMPLEMENTATION as sub-agent. (Tools: All tools)
- PRINCIPAL_UX_DESIGNER: Use it when asked for [PRINCIPAL_UX_DESIGNER] role (Tools: All tools)
- UX_UI_IMPLEMENTATION: Use it when asked for [UX_UI_IMPLEMENTATION] role (Tools: All tools)

When NOT to use: For specific file reads use Read/Glob; for specific class search use Glob.

Usage:
- Launch multiple agents in parallel with multiple tool uses in one message
- Use run_in_background parameter for async execution; use TaskOutput to retrieve results
- Use resume parameter with agent ID to continue previous agent with full context preserved
- Agent results aren't visible to user; summarize them in your response
- Agents with "access to current context" can see conversation history
- Tell agent if it should write code or just research
- Use proactively when agent description suggests it
