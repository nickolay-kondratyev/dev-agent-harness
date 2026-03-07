### We use change log to track the changes.
<change_log_help>
change_log - git-backed changelog for AI agents

Usage: change_log <command> [args]

Commands:
  create [title] [options]  Create changelog entry (prints JSON)
    --impact N              Impact level 1-5 (required)
    -t, --type TYPE         Type (feature|bug_fix|refactor|chore|breaking_change|docs|default) [default: default]
    --desc TEXT             Short description (in query output)
    --details_in_md TEXT    Markdown body (visible in show, NOT in query output)
    -a, --author NAME       Author [default: git user.name]
    --tags TAG,TAG,...      Comma-separated tags
    --dirs DIR,DIR,...      Comma-separated affected directories
    --ap KEY=VALUE          Anchor point (repeatable)
    --note-id KEY=VALUE     Note ID reference (repeatable)
  ls|list [--limit=N]       List entries (most recent first)
  show <id>                 Display entry
  edit <id>                 Open entry in $EDITOR
  add-note <id> [text]      Append timestamped note (text or stdin)
  query [jq-filter]         Output entries as JSONL (requires jq for filter)
  help                      Show this help

Entries stored as markdown in ./_change_log/ (auto-created at nearest .git root)
Override directory with CHANGE_LOG_DIR env var

- BEFORE_STARTING: query for recent changes to gather the context of recent relevant changes that were made.
- AFTER_COMPLETION of TOP_LEVEL_AGENT work: Add change log entry with clear title, concise --desc, and use --details_in_md for longer explanations. Sub-agents should NOT write change log.
</change_log_help>

### We use ticket to track tasks
This project uses a CLI ticket system=[tk] for task management.

- USE `tk` to manage tasks outside of your current session.
- USE `tk` to create new ticket for follow-up items.
  - Including any found pre-existing test failures.
- USE `tk` to close tickets for completed tasks.
<tk help>
ticket - minimal ticket system with dependency tracking

Usage: ticket <command> [args]

Commands:
  create [title] [options] Create ticket, prints JSON with id and full_path
    -d, --description      Description text. Goes into markdown body of the ticket.
                           MUST be self contained, if referencing files make sure they are referenced
                           with full relative path from git repo. And NOT just the file names.
                           This should give GOOD context for new agent picking this up.
                           For newlines use bash $'...\n...' quoting, e.g.:
                             -d $'First line.\n\nSecond paragraph.\n- bullet'
    --design               Design notes
    --acceptance           Acceptance criteria
    -t, --type             Type (bug|feature|task|epic|chore) [default: task]
    -p, --priority         Priority 0-4, 0=highest [default: 2]
    -a, --assignee         Assignee
    --external-ref         External reference (e.g., gh-123, JIRA-456)
    --parent               Parent ticket ID
    --tags                 Comma-separated tags (e.g., --tags ui,backend,urgent)
  start <id>               Set status to in_progress
  close <id>               Set status to closed
  reopen <id>              Set status to open
  status <id> <status>     Update status (open|in_progress|closed)
  dep <id> <dep-id>        Add dependency (id depends on dep-id)
  dep tree [--full] <id>   Show dependency tree (--full disables dedup)
  dep cycle                Find dependency cycles in open tickets
  undep <id> <dep-id>      Remove dependency
  link <id> <id> [id...]   Link tickets together (symmetric)
  unlink <id> <target-id>  Remove link between tickets
  ls|list [--status=X] [-a X] [-T X]   List tickets
  ready [-a X] [-T X]      List open/in-progress tickets with deps resolved
  blocked [-a X] [-T X]    List open/in-progress tickets with unresolved deps
  closed [--limit=N] [-a X] [-T X] List recently closed tickets (default 20, by mtime)
  show <id>                Display ticket
  edit <id>                Open ticket in $EDITOR
  add-note <id> [text]     Append timestamped note (or pipe via stdin)
  query [jq-filter]        Output tickets as JSONL (includes full_path)

Searches parent directories for _tickets/, stopping at .git boundary (override with TICKETS_DIR env var)
Tickets stored as markdown files in _tickets/ (filenames derived from title)
IDs are stored in frontmatter at 'id' field;
</tk help>

### Tags for change_log and tk
<tags to use for change_log and tk>
harness           # agent harness core orchestration
harness.workflow  # workflow phases and coordination
harness.cli       # CLI interface and argument parsing
asgardCore        # asgard core foundation library integration
agents            # agent management and communication
file-io           # file-based communication between agents
docs              # documentation and CLAUDE.md updates
</tags to use for change_log and tk>

### WHEN Closing ticket THEN compress change log
When you close the ticket see which change_log files have been created on your branch and compress them into one coherent change log.
