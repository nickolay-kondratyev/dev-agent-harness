package com.glassthought.shepherd.core.agent.rolecatalog

import java.nio.file.Path

/**
 * Parsed representation of a single agent role from the role catalog.
 *
 * Each role is loaded from a `.md` file whose YAML frontmatter contains at minimum
 * a `description` field. The [name] is derived from the filename (without extension).
 *
 * @param name Role name, derived from the filename without extension (case preserved).
 * @param description Short description from the `description` frontmatter field (required).
 * @param descriptionLong Extended description from the `description_long` frontmatter field (optional).
 * @param filePath Absolute path to the source `.md` file.
 */
data class RoleDefinition(
    val name: String,
    val description: String,
    val descriptionLong: String?,
    val filePath: Path,
)
