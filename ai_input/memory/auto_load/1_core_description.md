## Project Overview

CLI Kotlin Agent Harness — orchestrates work by calling out to other agents and coordinating their output.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### Architecture Principles
- Constructor injection (manual DI, no framework, no singletons)
- Structured logging via Out/OutFactory (never println)
- Structured exceptions extending AsgardBaseException
- Composition over inheritance
- Favor immutability
