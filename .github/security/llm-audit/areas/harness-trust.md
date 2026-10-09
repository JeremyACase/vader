# Harness and core-server trust

The attacker is a compromised or malicious harness Job, or any pod in the cluster that can reach
core-server.

- How does a harness prove which assignment it holds when it calls the `taskagent` endpoints
  (`AgentAssignmentController`)? Can a caller act on an assignment it wasn't given by guessing or
  reusing ids?
- Can a harness keep acting after its attempt is terminal, or report another attempt's outcome?
- Are the server-side turn, token and deadline budgets enforced on every path, or only trusted
  from the harness?
- In `core-agent-harness`, how is LLM output turned into tool calls, and can a model reply make the
  harness call something outside the tool set it was given?
