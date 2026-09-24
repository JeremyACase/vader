Several kinds of items in one file is normal Rust. A Rust file isn't the equivalent of a Java class; it's a module, closer to a Java package or a C++ translation unit plus its header. Two things follow:

  - Privacy is per module, not per type. Anything without pub is visible only inside its file. TurnOutcome is a private detail of the runner loop, so it lives in runner.rs. Moving it to its own file would
    force it to be marked pub(crate), making it more visible than it needs to be.
  - Tests live at the bottom of the file they test. The #[cfg(test)] mod tests block is compiled only for tests and can reach private items, so there's no need for the Java habit of loosening visibility just
    so tests can see things. It's standard practice, and CLAUDE.md requires it.

  runner.rs looks bigger than it is. 409 of its 688 lines are tests; the real code is 279 lines. The rule of thumb is to group by cohesion: a type, its impl, and the small private helpers and enums that exist
  only to serve it all belong together. Split a file when it holds two separate concerns, not when it holds several kinds of item.

  What I'd actually change:
  1. Hand-written conversion functions should be From implementations. to_message_body, to_role_body, to_tool_call_body, from_tool_call_body and result_body_for in core_server_adapter.rs are exactly what the
     standard From trait is for. With it, body.into() works everywhere and the conversion sits next to the type. This is the least idiomatic part of the crate.
  2. The conversation types live in the wrong place. ConversationMessage, ToolCall and InferenceTurn are defined in inference_gateway.rs, the file that holds the trait. So tool_executor.rs depends on the
     inference module just to get ToolCall. A conversation.rs for the shared types would make the dependencies read correctly.
  3. Two things in runner.rs don't belong there, and you're right about these:
     - The prompt text: TASK_INSTRUCTIONS, EMPTY_REPLY_NUDGE and OUTPUT_CAP_FAILURE_REASON are content, not loop logic, and would sit better in a prompts.rs.
     - HarnessOutcome: it's the run's public result type, which the adapter reports to core-server, so it belongs with ControlPlane or in its own module.
  4. The newtypes have public fields. TaskId(pub Uuid) lets any code reach in, construct one from any UUID, or take the UUID back out. That defeats the point of wrapping it, which is making the compiler reject
     a task id where an assignment id is expected. Make the field private and expose a getter.
  5. Smaller things:
     - The usual layout is a thin main.rs over a lib.rs that holds the modules; right now everything is in the binary.
     - For async services, tracing is more common than log + env_logger, since it can attach structured fields such as the assignment id to every log line.
     - The error variants flatten their causes into Strings. That's deliberate, since they're reported to core-server as short strings, but it loses the underlying error chain.

  Already idiomatic:
  - Static dispatch through generics rather than dyn Trait.
  - async fn directly in traits, without the async-trait crate.
  - thiserror for the error enum.
  - Newtype ids, apart from the public fields.
  - Result with ? throughout, and no unwrap outside tests.
  - A clean clippy -D warnings build.

  The .clone() calls in the adapter are fine; converting borrowed data into owned request bodies has to copy it somewhere.

  One caution about CLAUDE.md. The Rust section already adapts most Java conventions sensibly. The one that fights the language is "single return, complexity 1–3". In Rust, early return, ? and exhaustive match
  are the idiomatic way to stay flat, and forcing a single exit point sometimes makes Rust less readable. I'd loosen that rule for Rust specifically.

  If you want a concrete first step: items 1, 2 and the newtype fix are an afternoon's work, and moving the prompt text out of runner.rs is ten minutes.
