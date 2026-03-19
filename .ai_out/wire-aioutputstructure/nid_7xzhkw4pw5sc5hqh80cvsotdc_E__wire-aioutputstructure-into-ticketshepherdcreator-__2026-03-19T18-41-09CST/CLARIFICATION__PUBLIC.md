# Clarification

## Resolved Ambiguities

1. **ContextForAgentProviderImpl does NOT need AiOutputStructure** — paths come via AgentInstructionRequest from callers
2. **TicketShepherd doesn't exist yet** — cannot wire
3. **AiOutputStructure already injected** into TicketShepherdCreatorImpl constructor

## Awaiting Human Input

Question: How to provide parts to `ensureStructure()` in `create()` when workflow parsing isn't implemented yet?
Options: (A) add parts param to create(), (B) inject via constructor, (C) call with current parts (empty, evolves later)
