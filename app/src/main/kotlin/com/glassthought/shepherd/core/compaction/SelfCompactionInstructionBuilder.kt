package com.glassthought.shepherd.core.compaction

import com.glassthought.shepherd.core.context.ProtocolVocabulary
import java.nio.file.Path

/**
 * Builds the instruction text sent to an agent whose context window is running low,
 * asking it to summarize the current chat into a PRIVATE.md file and signal completion.
 *
 * See context window self-compaction spec (ref.ap.8nwz2AHf503xwq8fKuLcl.E).
 */
class SelfCompactionInstructionBuilder {

    /**
     * Renders the self-compaction instruction template with the given PRIVATE.md path.
     *
     * @param privateMdAbsolutePath absolute path where the agent should write its summary
     * @return fully rendered instruction text ready to be sent to the agent
     */
    fun build(privateMdAbsolutePath: Path): String {
        val callbackCommand =
            "${ProtocolVocabulary.CALLBACK_SIGNAL_SCRIPT} ${ProtocolVocabulary.Signal.SELF_COMPACTED}"

        return """Your context window is running low. Summarize this chat into
            |`$privateMdAbsolutePath` so work can continue in a new chat.
            |
            |Preserve all context needed for a new chat to understand:
            |- What we're doing and why
            |- At which point we are in the work
            |- All challenges we've had and how we've solved them
            |- Key decisions made and why
            |- Any patterns or discoveries about the codebase
            |
            |Make the summary as **concise** as possible but context rich.
            |
            |After writing the file, signal completion:
            |`$callbackCommand`""".trimMargin()
    }
}
