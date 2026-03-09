## Project Overview

CLI Kotlin Agent Harness — orchestrates work by calling out to other agents and coordinating their output.

### High level approach
- We will use TMUX to start up sessions so that we can communicate with the agents.

### Key Characteristics
- **CLI application** built in Kotlin (JVM)
- **Agent coordination**: manages workflow phases, file-based communication between agents
- **NOT thorg-specific** — general-purpose agent harness

### Dependencies
- Will take dependencies on well established third-party libraries.
- **Depends on asgardCore** (Out/OutFactory logging, ProcessRunner, AsgardCloseable, coroutines)
