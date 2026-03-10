package com.glassthought.chainsaw.core.server

/**
 * Handles agent-to-harness requests, decoupling HTTP protocol concerns in [KtorHarnessServer]
 * from phase-runner business logic.
 *
 * Injected into [KtorHarnessServer] so the server only owns HTTP parsing and routing.
 * The phase runner supplies the real implementation; [NoOpAgentRequestHandler] serves
 * as a placeholder until the phase runner wires in behavior.
 */
interface AgentRequestHandler {
    suspend fun onDone(request: AgentDoneRequest)
    suspend fun onQuestion(request: AgentQuestionRequest): String  // blocking, returns answer
    suspend fun onFailed(request: AgentFailedRequest)
    suspend fun onStatus(request: AgentStatusRequest)
}

/** Placeholder implementation — no behavior. Used until the phase runner wires in real logic. */
class NoOpAgentRequestHandler : AgentRequestHandler {
    override suspend fun onDone(request: AgentDoneRequest) = Unit
    override suspend fun onQuestion(request: AgentQuestionRequest): String = ""
    override suspend fun onFailed(request: AgentFailedRequest) = Unit
    override suspend fun onStatus(request: AgentStatusRequest) = Unit
}
