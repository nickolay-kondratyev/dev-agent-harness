---
id: nid_kzz296dqtpojvf3gp29827xtk_E
title: "fix PATH issue on start"
status: open
deps: []
links: []
created_iso: 2026-03-20T20:02:51Z
status_updated_iso: 2026-03-20T20:02:51Z
type: task
priority: 3
assignee: CC_opus-v4.6_WITH-nickolaykondratyev
---

WHEN running basic tests ran into the issue of not being able to correctly send  `callback_shepherd.signal.sh`. FIX it so that agents that are spawned in TMUX have correct PATH setup that includes the directory with callback_shepherd scripts, make sure the directory exists and has the scripts when setting the PATH up to validate that is the case. 

Got exit code 127 when attempting to call `callback_shepherd.signal.sh`
```
     Starting top_level_all_L1.sh
     _ +2 lines (ctrl+o to see all)
  _ _Interrupted · What should Claude do instead?

_ can you check why callback_shepherd.signal.sh started failed is it a PATH issue and the directory with the script wasnt added to the PATHs?

_ Searched for 2 patterns (ctrl+o to expand)

_ Yes, it's a PATH issue. The script exists at app/src/main/resources/scripts/callback_shepherd.signal.sh but that directory is not in the PATH.

  Let me check how the harness is supposed to inject this into the PATH for spawned agents:

_ Searching for 4 patterns_ (ctrl+o to expand)

_ Wrangling_ (39s · _ 564 tokens · thinking)
```


Reply from agent that couldnt find this:
```
_ The built script exists. The fix is for the harness to ensure export PATH=$PATH:/home/nickolaykondratyev/git_repos/nickolay-kondratyev_dev-agent-harness-mirror-1/app/build/resources/main/scripts is set before launching the agent in
  tmux. This should already happen automatically via ClaudeCodeAdapter when the agent is spawned normally.

  Summary: Yes, it's a PATH issue. The harness adds the scripts dir to PATH via export PATH=$PATH:$callbackScriptsDir in the tmux command, but this agent wasn't launched through that flow _ so the PATH never got that directory. When
  the harness launches agents correctly via ClaudeCodeAdapter, this works automatically.

_ Crunched for 49s
```