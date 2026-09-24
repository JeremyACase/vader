It's an 11.5k-line service with a solid architecture, but the code has built up more ceremony than it needs. Here's what bothers me, most important first. Some of it I contributed myself.

  1. Adding one kind of LLM call touches about eight files. Decomposition, evaluation, reattempt decisions, plan refinement and inference turns each have their own:
  - request model, LlmRequestKind value, submitX method on LlmRequestQueue, and case in LlmRequestInbox's switch;
  - *LlmExecutor and *Outcome class;
  - interface, Local* and Static* strategy classes.

  Most of those are the same code with different types. The Local* strategies are identical thin wrappers around the queue, and every *Outcome has the same "value or unreachable reason" shape. One generic
  LLM-call type, with a single queue and executor dispatch, would remove most of it.

  2. The canned test mode is faked at the wrong level. There are 7 Static* classes and 19 @ConditionalOnProperty switches on orchestrator.type, all to support vader.mode=TEST. That's a second, parallel
  implementation of every LLM feature. It also means the test pipeline never exercises the real prompts or response parsing. Faking one layer lower, with a single scripted Spring AI ChatModel bean, would
  delete that parallel implementation and let TEST mode run the real code paths.

  3. Field injection is costing more than it saves. CLAUDE.md requires it, but the tests need 166 ReflectionTestUtils.setField calls to wire things up. Dependencies are also hidden from the class's signature,
  so a service can quietly grow to a dozen collaborators without anything looking wrong. Constructor injection with final fields is what Spring itself recommends. This directly contradicts a rule you wrote, so
  it's your call, but I think it's the root of #4.

  4. There are god services. TaskAgentService (519 lines) covers the dispatch lifecycle, building the assignment's context, heartbeats, results, inference with transcript writing, and tool invocation with
  auditing and task scoping. OrchestratorAgentService mixes decomposition, validation and reattempt decisions. Each should split along those lines.

  5. The comments tell stories instead of stating rules. About 31% of lines are comments, and many are war stories: "qwen2.5:3b did exactly that twice in a row", "Ollama returns an empty string, not null…". I
  wrote plenty of those this session. They age badly, and the history belongs in commits and changelogs. A comment should state the rule the code keeps and why, in a sentence or two.

  6. The packages don't say where things live.
  - service/ holds everything: config, io, llm, operators, builders, decorators, initializers, tools.
  - Strategies live in two places, service/strategies/* and service/agent/*/strategies.
  - models/ is a mix of 34 classes: harness wire formats, internal events, LLM requests and verdicts.

  Organizing by feature (task agent, orchestration, llm, sandbox, storage, dao) would make things much easier to find.

  7. Some conventions add friction. The Interface/Abstract prefixes, the pattern name forced onto every class, and the single-return / no-early-return rule sometimes produce awkward code: a class called a
  strategy that's really just a proxy, or a var result = … assigned only so it can be returned. This one's milder, and a matter of taste.

  8. One question rather than a complaint: is the database-backed LLM queue worth what it costs today? It polls every 100–200ms, uses outbox rows and stall detection, and every LLM call makes a JSON round trip
  through the database. That makes sense if you run several core-server replicas sharing one Ollama slot. If you run one replica in practice, it's the most complex part of the system for a benefit you aren't
  using yet.

  What I'd keep: the architecture holds up well. The harness never talks to an LLM directly, everything is audited, an unreachable LLM always fails loudly with no silent fallbacks, entities and DTOs are
  cleanly separated, and test coverage is strong. The problems are how the code is organized and how much repetition there is, not the design.

  If I had to pick one thing, I'd do #2 and #1 together: collapse the LLM plumbing and move the fake down to the ChatModel. That would remove the most code and risk for the least disruption.

  What's on your list?
