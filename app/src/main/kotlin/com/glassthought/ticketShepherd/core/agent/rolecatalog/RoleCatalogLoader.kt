package com.glassthought.ticketShepherd.core.agent.rolecatalog

import com.asgard.core.data.value.Val
import com.asgard.core.data.value.ValType
import com.asgard.core.out.OutFactory
import com.glassthought.ticketShepherd.core.supporting.ticket.YamlFrontmatterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Loads agent role definitions from a directory of `.md` files.
 *
 * Each markdown file in the directory represents a role. Its YAML frontmatter must contain
 * a required `description` field and an optional `description_long` field.
 *
 * See design doc: ref.ap.iF4zXT5FUcqOzclp5JVHj.E (Role Catalog -- Auto-Discovered)
 *
 * @throws IllegalArgumentException if the directory does not exist, contains no `.md` files,
 *   or if any role file is missing the required `description` frontmatter field.
 */
interface RoleCatalogLoader {

    /**
     * Scans [dir] for `.md` files (flat, non-recursive) and returns a [RoleDefinition] for each.
     *
     * @throws IllegalArgumentException if [dir] does not exist or is not a directory,
     *   if no `.md` files are found, or if a role is missing the required `description` field.
     */
    suspend fun load(dir: Path): List<RoleDefinition>

    companion object {
        fun standard(outFactory: OutFactory): RoleCatalogLoader = RoleCatalogLoaderImpl(outFactory)
    }
}

/**
 * Default implementation of [RoleCatalogLoader].
 *
 * Reads each `.md` file from disk (on IO dispatcher), parses YAML frontmatter via
 * [YamlFrontmatterParser], and constructs [RoleDefinition] instances. Fails fast on
 * missing directory, empty catalog, or missing required frontmatter fields.
 */
class RoleCatalogLoaderImpl(outFactory: OutFactory) : RoleCatalogLoader {

    private val out = outFactory.getOutForClass(RoleCatalogLoaderImpl::class)

    override suspend fun load(dir: Path): List<RoleDefinition> {
        out.debug("loading_role_catalog") {
            listOf(Val(dir.toString(), ValType.FILE_PATH_STRING))
        }

        val roles = withContext(Dispatchers.IO) {
            require(Files.exists(dir) && Files.isDirectory(dir)) {
                "Role catalog directory does not exist or is not a directory: $dir"
            }

            val mdFiles = Files.walk(dir, 1).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension == "md" }
                    .toList()
            }

            require(mdFiles.isNotEmpty()) {
                "No .md files found in role catalog directory: $dir"
            }

            mdFiles.map { file ->
                val content = file.readText()
                val result = YamlFrontmatterParser.parse(content)

                val description = result.yamlFields["description"]
                    ?: throw IllegalArgumentException(
                        "Role file [${file.fileName}] is missing required frontmatter field: description"
                    )

                val descriptionLong = result.yamlFields["description_long"]

                RoleDefinition(
                    name = file.nameWithoutExtension,
                    description = description,
                    descriptionLong = descriptionLong,
                    filePath = file,
                )
            }
        }

        out.info(
            "role_catalog_loaded",
            Val(roles.size.toString(), ValType.STRING_USER_AGNOSTIC),
        )

        return roles
    }
}
