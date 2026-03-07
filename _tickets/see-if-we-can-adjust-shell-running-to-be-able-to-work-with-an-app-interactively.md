---
id: nid_8r4owxcdb41e8su29vblagn4j_E
title: "see if we can adjust shell running to be able to work with an app interactively."
status: in_progress
deps: []
links: []
created_iso: 2026-03-07T17:16:25Z
status_updated_iso: 2026-03-07T17:18:44Z
type: 
priority: 
assignee: nickolaykondratyev
---


WHEN I tried to run 
```
            val result = runner.runProcess("claude", "Say hello")

            println(result.trim())

            println("AFTER CLAUDE CALL BACK TO KOTLIN")

```

I did not get the interactive claude session.

I am wondering whether we can make our own shell interactive runner in this package that will allow us to run 'claude' and other application that require interactivity from within kotlin. TO start the process such that its input stream is coming from terminal, and we are able to interact with the process that was spawned from kotlin from terminal. 

```
> Task :app:run
Picked up JAVA_TOOL_OPTIONS: -Dkotlinx.coroutines.debug
Hello World!
{"level":"info","log_level":"info","clazz":"ProcessRunnerImpl","message":"Running shell command","values":[{"value":"[claude, Say hello]","type":"SHELL_COMMAND"}],"sequenceNum":0,"timestamp":"2026-03-07T17:15:22.651044738Z","origin":"server","thread":{"name":"main @coroutine#1","id":1},"pid":624081}

The Daemon will expire after the build after running out of JVM Metaspace.
The project memory settings are likely not configured or are configured to an insufficient value.
The daemon will restart for the next build, which may increase subsequent build times.
These settings can be adjusted by setting 'org.gradle.jvmargs' in 'gradle.properties'.
The currently configured max heap space is '512 MiB' and the configured max metaspace is '384 MiB'.
For more information on how to set these values, please refer to https://docs.gradle.org/9.2.1/userguide/build_environment.html#sec:configuring_jvm_memory in the Gradle documentation.
To disable this warning, set 'org.gradle.daemon.performance.disable-logging=true'.
Daemon will be stopped at the end of the build after running out of JVM Metaspace
<============-> 96% EXECUTING [51s]
> :app:run

```

no-review: let's see if can get a prototype of this going quickly 