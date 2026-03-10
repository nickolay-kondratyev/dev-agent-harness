package com.glassthought.chainsaw.core.ticket

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Reads a ticket markdown file and returns its structured [TicketData].
 *
 * A ticket is a markdown file with YAML frontmatter.
 * Required frontmatter fields: `id`, `title`.
 *
 * See design doc: ref.ap.mmcagXtg6ulznKYYNKlNP.E (CLI Entry Point section — ticket as required input)
 */
interface TicketParser {
    /**
     * Parses the ticket file at [path] and returns [TicketData].
     *
     * @throws IllegalArgumentException if [path] does not contain valid frontmatter,
     *   or if required fields `id` or `title` are missing.
     */
    suspend fun parse(path: Path): TicketData

    companion object {
        fun standard(outFactory: OutFactory): TicketParser = TicketParserImpl(outFactory)
    }
}

/**
 * Default implementation of [TicketParser].
 *
 * Reads the file from disk (on IO dispatcher) and delegates frontmatter parsing to
 * [YamlFrontmatterParser]. Fails fast if required fields `id` or `title` are absent.
 */
class TicketParserImpl(outFactory: OutFactory) : TicketParser {

    private val out = outFactory.getOutForClass(TicketParserImpl::class)

    companion object {
        private val RESERVED_KEYS = setOf("id", "title", "status")
    }

    override suspend fun parse(path: Path): TicketData {
        out.debug("reading_ticket") {
            listOf(Val(path.toString(), ValType.FILE_PATH_STRING))
        }

        val content = withContext(Dispatchers.IO) { path.readText() }

        val result = YamlFrontmatterParser.parse(content)

        val id = result.yamlFields["id"]
            ?: throw IllegalArgumentException("Ticket is missing required field: id")

        val title = result.yamlFields["title"]
            ?: throw IllegalArgumentException("Ticket is missing required field: title")

        val status = result.yamlFields["status"]

        val additionalFields = result.yamlFields
            .filterKeys { it !in RESERVED_KEYS }

        out.info(
            "ticket_parsed",
            Val(id, ValType.STRING_USER_AGNOSTIC),
            Val(title, ValType.STRING_USER_AGNOSTIC),
        )

        return TicketData(
            id = id,
            title = title,
            status = status,
            description = result.body,
            additionalFields = additionalFields,
        )
    }
}
