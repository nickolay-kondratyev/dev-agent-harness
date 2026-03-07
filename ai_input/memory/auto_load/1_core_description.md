## Project Overview

CLI Kotlin Agent Harness — orchestrates work by calling out to other agents and coordinating their output.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### Architecture Principles
See `3_kotlin_standards.md` for full Kotlin development standards.
