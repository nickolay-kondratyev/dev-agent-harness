---
id: nid_90p54ba8i7vm4wju7su8s187w_E
title: "fix factory not wired"
status: in_progress
deps: []
links: []
created_iso: 2026-03-20T00:19:44Z
status_updated_iso: 2026-03-20T00:28:40Z
type: task
priority: 3
assignee: CC_sonnet-v4.6_WITH-nickolaykondratyev
---


```
{"level":"info","log_level":"info","clazz":"GitBranchManagerImpl","message":"branch_created_and_checked_out","values":[{"value":"nid_ak7cvgavffdupobhdiau3mn16_E__write-hello-world-in-shell-make-script-be-bc501e__try-1","type":"GIT_BRANCH_NAME"}],"sequenceNum":13,"timestamp":"2026-03-20T00:19:12.491923790Z","origin":"server","thread":{"name":"main @coroutine#1","id":1},"pid":418599}
Exception in thread "main" kotlin.NotImplementedError: An operation is not implemented: SetupPlanUseCaseFactory not yet wired for production
        at com.glassthought.shepherd.core.creator.TicketShepherdCreatorImpl._init_$lambda$0(TicketShepherdCreator.kt:120)
        at com.glassthought.shepherd.core.creator.TicketShepherdCreatorImpl.wireTicketShepherd(TicketShepherdCreator.kt:218)
        at com.glassthought.shepherd.core.creator.TicketShepherdCreatorImpl.create(TicketShepherdCreator.kt:159)
        at com.glassthought.shepherd.core.creator.TicketShepherdCreatorImpl$create$1.invokeSuspend(TicketShepherdCreator.kt)
        at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
        at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
        at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:263)
        at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:94)
        at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:70)
        at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
        at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:48)
        at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
        at com.glassthought.shepherd.cli.RunSubcommand.call(AppMain.kt:134)
        at com.glassthought.shepherd.cli.RunSubcommand.call(AppMain.kt:71)
        at picocli.CommandLine.executeUserObject(CommandLine.java:2045)
        at picocli.CommandLine.access$1500(CommandLine.java:148)
        at picocli.CommandLine$RunLast.executeUserObjectOfLastSubcommandWithSameParent(CommandLine.java:2465)
        at picocli.CommandLine$RunLast.handle(CommandLine.java:2457)
        at picocli.CommandLine$RunLast.handle(CommandLine.java:2419)
        at picocli.CommandLine$AbstractParseResultHandler.execute(CommandLine.java:2277)
        at picocli.CommandLine$RunLast.execute(CommandLine.java:2421)
        at picocli.CommandLine.execute(CommandLine.java:2174)
        at com.glassthought.shepherd.cli.AppMainKt.main(AppMain.kt:42)
 ```