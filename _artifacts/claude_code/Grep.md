A powerful search tool built on ripgrep

  Usage:
  - ALWAYS use Grep for search tasks. NEVER invoke `grep` or `rg` as a Bash command.
  - Full regex syntax. Filter by glob ("*.js") or type ("js", "py")
  - Output modes: "content" (lines), "files_with_matches" (paths, default), "count"
  - Uses ripgrep syntax - escape literal braces (`interface\{\}`)
  - For multiline patterns, use `multiline: true`
