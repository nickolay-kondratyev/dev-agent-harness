#!/usr/bin/env bash
# __enable_bash_strict_mode__

main() {
  ei2 is_in_docker

# shellcheck disable=SC2155
local tmp_file="$(ei2 make_tmp_file)"
cat >> "${tmp_file:?}" <<EOF
    READ specs under FOLDER=[./doc]. FOCUS on specs not code.

    Look at descriptions of tickets that are open [tk query | rg "open|in_progress" | rg SIMPLIFY_CANDIDATE]

    Look for TOP 5 NEW opportunities (not captured in tickets) where we can BOTH SIMPLIFY & Improve robustness. IF there is something that is overly complex that can be solved JUST as well or BETTER with a simpler approach. WITHOUT decreasing robustness, but rather increasing it.

    For each opportunity that you find create a new ticket with title of [SIMPLIFY_CANDIDATE: <description>].

    THERE could be no such opportunities in SUCH case write file $HOME/done_with_simplification.md with reasons and do not create tickets.
EOF

  for i in {1..1} ; do
      if [[ -f "$HOME/done_with_simplification.md" ]]; then
        echo.green "$HOME/done_with_simplification.md file exists. Exitting."
      	exit 1

      fi

      git.save

      cdi /home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1

      ai_core --agent CC --model opus --print "Read and execute instructions in [${tmp_file:?}]"

      git.save
  done
}

main "${@}"
