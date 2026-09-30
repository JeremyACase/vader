//! Every piece of text the harness sends to the model or reports as a failure reason, kept apart
//! from the loop logic that decides when to send it.

/// Frames the action-loop protocol for the model: call tools as needed, and a plain-text reply
/// with no further tool calls is what ends the run. Without this, a model has no way to know that
/// declining to call a tool is itself the signal to stop -- it would otherwise be indistinguishable
/// from simply not having tried yet.
///
/// Explicitly forbids asking a clarifying question as the final reply: nothing in this loop ever
/// reads a response back to the model, so a question is never answered -- it just gets reported
/// as though it were the finished result. The user message that follows always carries the
/// background (the original request, any attached files, and any prerequisite tasks' results)
/// that a lone task description wouldn't otherwise include, specifically so the model has what it
/// needs to avoid needing to ask in the first place.
///
/// Says nothing about creating or staging into a sandbox: `core-server` provisions the attempt's
/// own sandbox and stages every attached file into it on the first `run_python_code` call, so
/// the model never has a sandbox name to carry between calls (and get wrong).
pub const TASK_INSTRUCTIONS: &str =
    "You are an autonomous agent completing one task from a larger \
    workflow. No human is available to answer follow-up questions during this run -- you must \
    gather anything you need yourself, using your available tools, rather than asking a \
    clarifying question. The next message gives you the task plus background: the original \
    request, any files attached to it, and the results of any prerequisite tasks. If a file is \
    mentioned, do not assume you already know what it contains: every attached file is already \
    in your Python working directory, so inspect it with run_python_code, opening it by exactly \
    the filename you are given. Wait for each tool result before relying on it in a later call. \
    When you have fully completed the task, reply \
    with your final answer as plain text and do not call any more tools; that reply is what ends \
    this run and is treated as your finished result, not a question. Never end the run by asking \
    a question or requesting clarification -- if something is genuinely still missing after using \
    your tools, state your best attempt and explain what was missing instead.";

/// Sent back to the model when it ends a turn with neither tool calls nor any text. Small local
/// models occasionally return a completely empty message; treating that as the final answer
/// would report the task `Succeeded` with no output at all, so the run is nudged to keep going
/// instead. A model that keeps answering blank is still bounded: every blank turn fingerprints
/// identically, so the stall detector ends the run as `Stalled` rather than looping forever.
pub const EMPTY_REPLY_NUDGE: &str =
    "Your last reply was empty -- it contained no text and no tool \
    calls. Continue the task: call a tool if you still need information, or reply with your \
    final answer as plain text.";

/// Sent back when a final answer contains a fenced code block the model never executed. Small
/// models often end a run with "here's the corrected approach, let's proceed" plus code they never
/// ran -- reported as-is, that text is handed downstream as though the work were done.
pub const UNEXECUTED_CODE_NUDGE: &str = "Your reply contains code that you have not run, so it \
    is not a finished result yet: nothing in it has been executed. Run the code with \
    run_python_code and base your answer on its output. If the code itself is the deliverable, \
    reply again with the same final answer.";

/// Sent back when a final answer follows a tool call that failed, with nothing succeeding since --
/// whatever that call was doing is still undone, however confident the reply sounds.
pub const UNRESOLVED_ERROR_NUDGE: &str = "Your last tool call failed and nothing has succeeded \
    since, so the work it was doing is not done yet. Fix the problem and run it again. If the task \
    is genuinely complete regardless, reply again with the same final answer.";

/// Reported as the failure reason when a turn is cut off at the output token cap. Failing the
/// attempt outright, rather than nudging, is deliberate: at temperature 0 the same conversation
/// just runs into the same cap again, and a clear reason lets the reattempt decision (and a human
/// reading the task) see exactly what went wrong instead of an empty reply or a generic stall.
pub const OUTPUT_CAP_FAILURE_REASON: &str = "The model's reply was cut off at the output token \
    cap before it finished, so its answer or tool call was incomplete and could not be used.";

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prompts_have_no_runs_of_spaces_from_line_wrapping() {
        [
            TASK_INSTRUCTIONS,
            EMPTY_REPLY_NUDGE,
            UNEXECUTED_CODE_NUDGE,
            UNRESOLVED_ERROR_NUDGE,
            OUTPUT_CAP_FAILURE_REASON,
        ]
        .iter()
        .for_each(|message| assert!(!message.contains("  "), "{message:?}"));
    }
}
