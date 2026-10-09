# MCP tool surface

The attacker is a task agent: a model reading a prompt, files or tool results an adversary may have
written, calling core-server's tools through the harness, or any client of the unauthenticated MCP
server.

- Which tools can each audience reach (`mcp` package: the tool registry and tool audiences)? Can an
  agent call a tool meant for another audience?
- Can an agent read or change another workflow's, task's or attempt's data through any tool,
  including the generic DAO query tools?
- `post_task_update` must only ever target the calling agent's own task, whatever task id the model
  supplies. Does every path enforce that?
- Does the MCP transport-security filter (allowed hosts and origins) cover every MCP endpoint?
