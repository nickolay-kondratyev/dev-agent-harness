package com.glassthought.ticketShepherd.core.supporting.ticket

/**
 * Parsed representation of a Shepherd ticket file.
 *
 * A ticket is a markdown file with YAML frontmatter.
 * [id] and [title] are required (parsed from frontmatter).
 * [status] is optional — not all markdown files parsed via [YamlFrontmatterParser] will have it.
 * [description] is the markdown body (content after the closing --- delimiter).
 * [additionalFields] holds all other frontmatter fields for extensibility.
 * Note: [id], [title], and [status] are NOT included in [additionalFields].
 */
data class TicketData(
    val id: String,
    val title: String,
    val status: String?,
    val description: String,
    val additionalFields: Map<String, String> = emptyMap(),
)
