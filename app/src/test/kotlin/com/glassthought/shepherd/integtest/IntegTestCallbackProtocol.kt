package com.glassthought.shepherd.integtest

/**
 * Shared callback protocol text for integration test system prompts.
 *
 * All integration test system prompts share the same callback protocol instructions.
 * This class extracts the common parts to avoid duplication across test helpers.
 */
internal object IntegTestCallbackProtocol {

    /**
     * The core callback protocol section shared by all integration test system prompts.
     * Includes startup, payload ACK, and done signaling instructions.
     */
    val CORE_PROTOCOL: String = """
        |## Callback Protocol
        |
        |You MUST use `callback_shepherd.signal.sh` (already on your PATH) to communicate with the harness.
        |
        |### On startup (FIRST thing you do):
        |```bash
        |callback_shepherd.signal.sh started
        |```
        |
        |### When you receive a payload wrapped in XML tags:
        |1. First ACK the payload:
        |```bash
        |callback_shepherd.signal.sh ack-payload <payload_id>
        |```
        |The payload_id is in the `payload_id` attribute of the XML tag.
        |
        |2. Then read and follow the instructions in the payload.
        |
        |### When done with work:
        |```bash
        |callback_shepherd.signal.sh done completed
        |```
    """.trimMargin()

    /**
     * The self-compaction extension to the callback protocol.
     */
    val SELF_COMPACTION_PROTOCOL: String = """
        |### When instructed to self-compact:
        |1. Write the summary file as instructed (PRIVATE.md at the specified path)
        |2. Then signal self-compaction:
        |```bash
        |callback_shepherd.signal.sh self-compacted
        |```
    """.trimMargin()

    /**
     * Standard important notes footer for all integration test system prompts.
     */
    val IMPORTANT_NOTES_BASE: String = """
        |## IMPORTANT
        |- ALWAYS call `callback_shepherd.signal.sh started` FIRST before doing anything else.
        |- ALWAYS ACK payloads before processing them.
        |- ALWAYS signal done when you finish processing a payload's instructions.
        |- Use Bash tool to execute the callback scripts.
    """.trimMargin()

    /**
     * Additional important notes for self-compaction prompts.
     */
    val IMPORTANT_NOTES_COMPACTION: String = """
        |- When asked to self-compact: write the PRIVATE.md file FIRST, then signal `self-compacted`.
        |- When asked to signal done: signal `done completed`.
    """.trimMargin()

    /**
     * Bootstrap message sent to agents on spawn.
     */
    const val BOOTSTRAP_MESSAGE: String =
        "You are a test agent. Your FIRST action must be " +
            "to call `callback_shepherd.signal.sh started` using " +
            "the Bash tool. This is critical — do it immediately " +
            "before anything else. After that, wait for further " +
            "instructions via payload delivery."
}
