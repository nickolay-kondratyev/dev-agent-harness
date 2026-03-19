---
id: nid_x56bo8icym4x66j0w7zjtvfuy_E
title: "Align coverage with sonar"
status: in_progress
deps: []
links: []
created_iso: 2026-03-19T22:05:18Z
status_updated_iso: 2026-03-19T22:06:24Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---

When I ran `./run_sonar.sh` i am getting issue shown as following in relation to to coverage i think.

ROOT cause and fix it so the coverage works well with sonar.
`File 'ShepherdContext.kt' not found in project sources`

```
> Task :app:koverXmlReport
[stderr] Picked up JAVA_TOOL_OPTIONS: -Dkotlinx.coroutines.debug
[stderr] Picked up JAVA_TOOL_OPTIONS: -Dkotlinx.coroutines.debug
File 'AppMain.kt' not found in project sources
File 'ShepherdContext.kt' not found in project sources
File 'ContextWindowState.kt' not found in project sources
File 'SpawnedAgentHandle.kt' not found in project sources
File 'AgentPayload.kt' not found in project sources
File 'SpawnAgentConfig.kt' not found in project sources
File 'AgentFacadeImpl.kt' not found in project sources
File 'AgentSignal.kt' not found in project sources
File 'Clock.kt' not found in project sources
File 'TmuxCommunicator.kt' not found in project sources
File 'TmuxSessionManager.kt' not found in project sources
File 'TmuxAllSessionsKiller.kt' not found in project sources
File 'TmuxSession.kt' not found in project sources
File 'WorkflowParser.kt' not found in project sources
File 'WorkflowDefinition.kt' not found in project sources
File 'InterruptHandler.kt' not found in project sources
File 'TmuxSessionName.kt' not found in project sources
File 'HandshakeGuid.kt' not found in project sources
File 'ResumableAgentSessionId.kt' not found in project sources
File 'PayloadAckTimeoutException.kt' not found in project sources
File 'AckedPayloadSender.kt' not found in project sources
File 'ShepherdServer.kt' not found in project sources
File 'PayloadId.kt' not found in project sources
File 'SignalCallbackDispatcher.kt' not found in project sources
File 'SignalRequests.kt' not found in project sources
File 'StraightforwardPlanUseCase.kt' not found in project sources
File 'DetailedPlanningUseCase.kt' not found in project sources
File 'PlanningPartExecutorFactory.kt' not found in project sources
File 'SetupPlanUseCase.kt' not found in project sources
File 'ShepherdValType.kt' not found in project sources
File 'TicketShepherd.kt' not found in project sources
File 'TicketShepherdCreator.kt' not found in project sources
File 'Constants.kt' not found in project sources
File 'TicketData.kt' not found in project sources
File 'YamlFrontmatterParser.kt' not found in project sources
File 'TicketParser.kt' not found in project sources
File 'ContextInitializer.kt' not found in project sources
File 'EnvironmentValidator.kt' not found in project sources
File 'SpawnTmuxAgentSessionParams.kt' not found in project sources
File 'SpawnExceptions.kt' not found in project sources
File 'SpawnTmuxAgentSessionUseCase.kt' not found in project sources
File 'SessionEntry.kt' not found in project sources
File 'SessionsState.kt' not found in project sources
File 'ClaudeCodeContextWindowStateReader.kt' not found in project sources
File 'ContextWindowStateUnavailableException.kt' not found in project sources
File 'ContextWindowSlimDto.kt' not found in project sources
File 'InstructionSection.kt' not found in project sources
File 'InstructionPlanAssembler.kt' not found in project sources
File 'InstructionText.kt' not found in project sources
File 'ContextForAgentProviderImpl.kt' not found in project sources
File 'ContextForAgentProvider.kt' not found in project sources
File 'HarnessTimeoutConfig.kt' not found in project sources
File 'AgentType.kt' not found in project sources
File 'TmuxCommandRunner.kt' not found in project sources
File 'ProcessResult.kt' not found in project sources
File 'AiOutputStructure.kt' not found in project sources
File 'CompactionTrigger.kt' not found in project sources
File 'SelfCompactionInstructionBuilder.kt' not found in project sources
File 'TmuxAgentSession.kt' not found in project sources
File 'CurrentStatePersistence.kt' not found in project sources
File 'SessionRecord.kt' not found in project sources
File 'CurrentState.kt' not found in project sources
File 'SubPartStateTransition.kt' not found in project sources
File 'ShepherdObjectMapper.kt' not found in project sources
File 'SubPartRole.kt' not found in project sources
File 'CurrentStateInitializer.kt' not found in project sources
File 'Part.kt' not found in project sources
File 'Phase.kt' not found in project sources
File 'PlanConversionException.kt' not found in project sources
File 'SubPartStatus.kt' not found in project sources
File 'AgentSessionInfo.kt' not found in project sources
File 'IterationConfig.kt' not found in project sources
File 'PartResult.kt' not found in project sources
File 'SubPart.kt' not found in project sources
File 'PlanFlowConverter.kt' not found in project sources
File 'TmuxStartCommand.kt' not found in project sources
File 'GitBranchManager.kt' not found in project sources
File 'CommitAuthorBuilder.kt' not found in project sources
File 'GitOperationFailureUseCase.kt' not found in project sources
File 'GitCommitStrategy.kt' not found in project sources
File 'WorkingTreeValidator.kt' not found in project sources
File 'CommitMessageBuilder.kt' not found in project sources
File 'GitCommandBuilder.kt' not found in project sources
File 'GitIndexLockFileOperations.kt' not found in project sources
File 'TryNResolver.kt' not found in project sources
File 'BranchNameBuilder.kt' not found in project sources
File 'PartCompletionGuard.kt' not found in project sources
File 'PartExecutorImpl.kt' not found in project sources
File 'PublicMdValidator.kt' not found in project sources
File 'InnerFeedbackLoop.kt' not found in project sources
File 'SubPartConfig.kt' not found in project sources
File 'PrivateMdValidator.kt' not found in project sources
File 'RejectionNegotiationUseCase.kt' not found in project sources
File 'ReInstructAndAwait.kt' not found in project sources
File 'RoleDefinition.kt' not found in project sources
File 'RoleCatalogLoader.kt' not found in project sources
File 'ProcessExiter.kt' not found in project sources
File 'ConsoleOutput.kt' not found in project sources
File 'UserInputReader.kt' not found in project sources
File 'DispatcherProvider.kt' not found in project sources
File 'GlmConfig.kt' not found in project sources
File 'AgentTypeAdapter.kt' not found in project sources
File 'ClaudeCodeAdapter.kt' not found in project sources
File 'FeedbackResolutionParser.kt' not found in project sources
File 'FailedToConvergeUseCase.kt' not found in project sources
File 'AgentUnresponsiveUseCase.kt' not found in project sources
File 'TicketFailureLearningUseCaseImpl.kt' not found in project sources
File 'DetectionContext.kt' not found in project sources
File 'TicketFailureLearningUseCase.kt' not found in project sources
File 'FailedToExecutePlanUseCase.kt' not found in project sources
File 'QaDrainAndDeliverUseCase.kt' not found in project sources
File 'QaAnswersFileWriter.kt' not found in project sources
File 'UserQuestionContext.kt' not found in project sources
File 'StdinUserQuestionHandler.kt' not found in project sources
File 'QuestionAndAnswer.kt' not found in project sources
File 'NonInteractiveAgentRunner.kt' not found in project sources
File 'NonInteractiveAgentRunnerImpl.kt' not found in project sources
File 'MutableSynchronizedMap.kt' not found in project sources
> Task :sonar
```