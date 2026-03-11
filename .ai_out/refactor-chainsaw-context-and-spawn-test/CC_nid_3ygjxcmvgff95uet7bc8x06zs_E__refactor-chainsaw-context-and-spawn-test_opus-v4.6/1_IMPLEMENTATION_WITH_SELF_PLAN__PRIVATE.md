# Implementation Plan: Refactor ChainsawContext

## Status: COMPLETE

## Goal
Introduce logical grouping of dependencies in ChainsawContext (TmuxInfra, DirectLlmInfra, Infra, UseCases) and simplify SpawnTmuxAgentSessionUseCaseIntegTest.

## Plan

### Step 1: Create grouping data classes in Initializer.kt
- [x] TmuxInfra (commandRunner, communicator, sessionManager)
- [x] DirectLlmInfra (glmDirectLLM, httpClient)
- [x] Infra (outFactory, tmux, directLlm)
- [x] UseCases (spawnTmuxAgentSession)

### Step 2: Refactor ChainsawContext constructor
- [x] Replace flat fields with Infra + UseCases
- [x] Keep anchor point

### Step 3: Update Initializer
- [x] Add systemPromptFilePath and claudeProjectsDir params
- [x] Wire up ClaudeCodeAgentStarterBundleFactory, DefaultAgentTypeChooser, SpawnTmuxAgentSessionUseCase

### Step 4: Update SharedContextIntegFactory
- [x] Pass systemPromptFilePath when initializing
- [x] Added resolveSystemPromptFilePath() and findGitRepoRoot() helper methods

### Step 5: Simplify SpawnTmuxAgentSessionUseCaseIntegTest
- [x] Use chainsawContext.useCases.spawnTmuxAgentSession
- [x] Use chainsawContext.infra.tmux.sessionManager
- [x] Remove manual construction and helper functions

### Step 6: Update TmuxCommunicatorIntegTest
- [x] chainsawContext.tmuxSessionManager -> chainsawContext.infra.tmux.sessionManager

### Step 7: Update TmuxSessionManagerIntegTest
- [x] Same change

### Step 8: Update AppDependenciesCloseTest
- [x] Updated manual ChainsawContext construction to use grouped structure

### Step 9: Update production code references
- [x] AppMain.kt: deps.tmuxSessionManager -> deps.infra.tmux.sessionManager
- [x] CallGLMApiSandboxMain.kt: .glmDirectLLM -> .infra.directLlm.glmDirectLLM

### Step 10: Update documentation
- [x] SharedContextDescribeSpec KDoc updated

### Step 11: Verify compilation
- [x] Unit tests pass (BUILD SUCCESSFUL)

## Commit
`7a01229` - Refactor ChainsawContext: introduce logical grouping (TmuxInfra, DirectLlmInfra, Infra, UseCases)
