# CLARIFICATION — Role Catalog Loader

## Status: No Ambiguities

The ticket is well-specified with clear requirements:
1. **Data model**: `RoleDefinition(name, description, descriptionLong?, filePath)`
2. **Interface**: `RoleCatalogLoader.load(dir: Path): List<RoleDefinition>`
3. **Reuse**: YamlFrontmatterParser from ticket package
4. **Fail-fast**: Missing `description`, non-existent/empty directory
5. **Naming**: Filename without extension, case preserved
6. **No filtering**: Every `.md` file is a role
7. **Package**: `com.glassthought.chainsaw.core.rolecatalog`
8. **No new deps**: snakeyaml already present

## THINK_LEVEL Assessment
**THINK** — This is a well-understood, straightforward feature with clear patterns to follow from TicketParser.
