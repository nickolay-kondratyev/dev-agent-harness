### We use change log to track the changes.
<change_log_help>
{{$(change_log --help)}}
</change_log_help>

### We use ticket to track tasks
This project uses a CLI ticket system=[tk] for task management.

- USE `tk` to manage tasks outside of your current session.
- USE `tk` to create new ticket for follow-up items.
  - Including any found pre-existing test failures.
- USE `tk` to close tickets for completed tasks.

<tk help>
{{$(tk --help)}}
</tk help>

### WHEN Closing ticket THEN compress change log
When you close the ticket see which change_log files have been created on your branch and compress them into one coherent change log.
