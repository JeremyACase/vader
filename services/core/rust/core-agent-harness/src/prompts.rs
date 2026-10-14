//! Every piece of text the harness sends the model. `core-server` renders all of it and hands it
//! over with the assignment, so Vader's prompts live, and are versioned, in one place; the
//! harness only decides *when* each one is sent.

use serde::{Deserialize, Serialize};

/// The prompts for one assignment.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssignmentPrompts {
    /// Frames the action-loop protocol: call tools as needed, and a plain-text reply with no
    /// further tool calls is what ends the run.
    pub system: String,
    /// The opening user message: the task's background, then the task itself.
    pub task: String,
    pub nudges: AssignmentNudges,
}

/// What to send back in place of accepting a reply as the run's final answer.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssignmentNudges {
    /// After a turn with neither tool calls nor any text. Treating an empty message as the final
    /// answer would report the task `Succeeded` with no output at all. A model that keeps
    /// answering blank is still bounded: every blank turn fingerprints identically, so the stall
    /// detector ends the run as `Stalled`.
    pub empty_reply: String,
    /// After a final answer carrying a fenced code block the model never executed.
    pub unexecuted_code: String,
    /// After a final answer that follows a failed tool call, with nothing succeeding since.
    pub unresolved_error: String,
}
