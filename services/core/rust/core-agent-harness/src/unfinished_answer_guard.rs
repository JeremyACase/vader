use serde_json::Value;

use crate::inference_gateway::{ConversationMessage, ConversationRole};

/// The tool whose `code` argument counts as "executed" when checking a final answer for code the
/// model never ran.
const RUN_PYTHON_CODE_TOOL: &str = "run_python_code";

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

/// Catches a final answer that describes work instead of reporting it -- code the model never
/// ran, or a reply that follows an unresolved tool error -- and supplies a nudge to send back
/// instead of ending the run.
///
/// Deliberately cheap to get past: at most `max_nudges` per run, and a model that replies with
/// the exact answer it was just nudged about is taken at its word (the code may genuinely be the
/// deliverable). Anything that slips through still faces `core-server`'s independent evaluation.
pub struct UnfinishedAnswerGuard {
    max_nudges: u32,
    nudges_sent: u32,
    last_nudged_answer: Option<String>,
}

impl UnfinishedAnswerGuard {
    pub fn new(max_nudges: u32) -> Self {
        Self {
            max_nudges,
            nudges_sent: 0,
            last_nudged_answer: None,
        }
    }

    /// Reviews a would-be final answer against the conversation so far, returning the nudge to
    /// send back when it looks unfinished, or `None` to accept it as the run's result.
    pub fn review(
        &mut self,
        answer: &str,
        messages: &[ConversationMessage],
    ) -> Option<&'static str> {
        let exhausted = self.nudges_sent >= self.max_nudges
            || self.last_nudged_answer.as_deref() == Some(answer);
        let nudge = if exhausted {
            None
        } else {
            unfinished_nudge_for(answer, messages)
        };
        if nudge.is_some() {
            self.nudges_sent += 1;
            self.last_nudged_answer = Some(answer.to_string());
        }
        nudge
    }
}

/// An unresolved tool error takes priority: code in the reply is usually the model's proposed fix
/// for exactly that error, and "fix it and run it again" covers both.
fn unfinished_nudge_for(answer: &str, messages: &[ConversationMessage]) -> Option<&'static str> {
    if last_tool_call_failed(messages) {
        Some(UNRESOLVED_ERROR_NUDGE)
    } else if has_unexecuted_code(answer, messages) {
        Some(UNEXECUTED_CODE_NUDGE)
    } else {
        None
    }
}

/// Whether the most recent tool result reports a failure: a non-zero `exitCode` or `timedOut`
/// (the `run_python_code` result shape). A result without those fields -- another tool, or a
/// plain string -- is never treated as a failure.
fn last_tool_call_failed(messages: &[ConversationMessage]) -> bool {
    messages
        .iter()
        .rev()
        .find(|message| message.role == ConversationRole::Tool)
        .and_then(|message| message.content.as_deref())
        .and_then(|content| serde_json::from_str::<Value>(content).ok())
        .is_some_and(|result| reports_failure(&result))
}

fn reports_failure(result: &Value) -> bool {
    let exited_non_zero = result
        .get("exitCode")
        .and_then(Value::as_i64)
        .is_some_and(|code| code != 0);
    let timed_out = result
        .get("timedOut")
        .and_then(Value::as_bool)
        .unwrap_or(false);
    exited_non_zero || timed_out
}

/// Whether the answer carries a fenced code block that no earlier `run_python_code` call
/// executed. Compared with all whitespace removed, so re-indenting or re-wrapping code the model
/// really did run -- or quoting an excerpt of it -- doesn't count as new code.
fn has_unexecuted_code(answer: &str, messages: &[ConversationMessage]) -> bool {
    let executed = executed_code(messages);
    fenced_code_blocks(answer)
        .into_iter()
        .map(|block| without_whitespace(&block))
        .filter(|block| !block.is_empty())
        .any(|block| !executed.iter().any(|code| code.contains(&block)))
}

fn executed_code(messages: &[ConversationMessage]) -> Vec<String> {
    messages
        .iter()
        .flat_map(|message| message.tool_calls.iter())
        .filter(|call| call.name == RUN_PYTHON_CODE_TOOL)
        .filter_map(|call| serde_json::from_str::<Value>(&call.arguments_json).ok())
        .filter_map(|arguments| arguments.get("code")?.as_str().map(without_whitespace))
        .collect()
}

/// The bodies of every ```-fenced block, minus any language tag on the opening fence. Text
/// between fences alternates with code, so the odd-indexed pieces of a split on the fence are
/// the blocks; an unterminated final fence still counts as a block.
fn fenced_code_blocks(answer: &str) -> Vec<String> {
    answer
        .split("```")
        .skip(1)
        .step_by(2)
        .map(strip_language_tag)
        .collect()
}

fn strip_language_tag(block: &str) -> String {
    let body = match block.split_once('\n') {
        Some((first_line, rest)) if is_language_tag(first_line) => rest,
        _ => block,
    };
    body.to_string()
}

fn is_language_tag(line: &str) -> bool {
    line.trim()
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '+' || c == '-' || c == '_')
}

fn without_whitespace(text: &str) -> String {
    text.chars().filter(|c| !c.is_whitespace()).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::inference_gateway::ToolCall;

    const CODE: &str = "import pandas as pd\ndf = pd.read_excel('data.xlsx')\ndf.head()";

    fn run_python_call(code: &str) -> ConversationMessage {
        let arguments_json = serde_json::json!({ "code": code }).to_string();
        ConversationMessage::assistant_tool_calls(vec![ToolCall {
            id: "call-1".to_string(),
            name: RUN_PYTHON_CODE_TOOL.to_string(),
            arguments_json,
        }])
    }

    fn python_result(exit_code: i64) -> ConversationMessage {
        let result = serde_json::json!({
            "stdout": "",
            "stderr": "",
            "exitCode": exit_code,
            "timedOut": false
        });
        ConversationMessage::tool_result("call-1", RUN_PYTHON_CODE_TOOL, result.to_string())
    }

    fn answer_with_code(code: &str) -> String {
        format!("Here's the corrected approach.\n```python\n{code}\n```\nLet's proceed.")
    }

    fn guard() -> UnfinishedAnswerGuard {
        UnfinishedAnswerGuard::new(2)
    }

    #[test]
    fn nudge_messages_have_no_runs_of_spaces_from_line_wrapping() {
        [UNEXECUTED_CODE_NUDGE, UNRESOLVED_ERROR_NUDGE]
            .iter()
            .for_each(|message| assert!(!message.contains("  "), "{message:?}"));
    }

    #[test]
    fn accepts_a_plain_answer() {
        assert_eq!(guard().review("The mean is 4.2.", &[]), None);
    }

    #[test]
    fn nudges_an_answer_carrying_code_that_was_never_run() {
        let answer = answer_with_code(CODE);
        assert_eq!(guard().review(&answer, &[]), Some(UNEXECUTED_CODE_NUDGE));
    }

    #[test]
    fn accepts_code_that_was_already_run_even_when_reformatted() {
        let messages = vec![run_python_call(CODE), python_result(0)];
        let reindented = CODE.replace('\n', "\n    ");
        assert_eq!(
            guard().review(&answer_with_code(&reindented), &messages),
            None
        );
    }

    #[test]
    fn accepts_an_excerpt_of_code_that_was_already_run() {
        let messages = vec![run_python_call(CODE), python_result(0)];
        assert_eq!(
            guard().review(&answer_with_code("df.head()"), &messages),
            None
        );
    }

    #[test]
    fn nudges_an_answer_that_follows_a_failed_tool_call() {
        let messages = vec![run_python_call(CODE), python_result(1)];
        assert_eq!(
            guard().review("It failed, but that's fine.", &messages),
            Some(UNRESOLVED_ERROR_NUDGE)
        );
    }

    #[test]
    fn accepts_an_answer_once_a_later_tool_call_succeeded() {
        let messages = vec![
            run_python_call("bad()"),
            python_result(1),
            run_python_call(CODE),
            python_result(0),
        ];
        assert_eq!(guard().review("The data has 7 columns.", &messages), None);
    }

    #[test]
    fn treats_a_timed_out_tool_call_as_failed() {
        let result = serde_json::json!({ "exitCode": 0, "timedOut": true }).to_string();
        let messages = vec![
            run_python_call(CODE),
            ConversationMessage::tool_result("call-1", RUN_PYTHON_CODE_TOOL, result),
        ];
        assert_eq!(
            guard().review("Done.", &messages),
            Some(UNRESOLVED_ERROR_NUDGE)
        );
    }

    #[test]
    fn ignores_tool_results_without_an_exit_status() {
        let messages = vec![ConversationMessage::tool_result(
            "call-1",
            "get_object_content",
            "\"some text\"",
        )];
        assert_eq!(guard().review("Done.", &messages), None);
    }

    #[test]
    fn accepts_the_same_answer_repeated_after_a_nudge() {
        let mut guard = guard();
        let answer = answer_with_code(CODE);
        assert!(guard.review(&answer, &[]).is_some());
        assert_eq!(guard.review(&answer, &[]), None);
    }

    #[test]
    fn stops_nudging_once_the_cap_is_reached() {
        let mut guard = UnfinishedAnswerGuard::new(1);
        assert!(guard.review(&answer_with_code("a = 1"), &[]).is_some());
        assert_eq!(guard.review(&answer_with_code("b = 2"), &[]), None);
    }

    #[test]
    fn finds_an_unterminated_code_block() {
        assert_eq!(fenced_code_blocks("text\n```python\nx = 1"), vec!["x = 1"]);
    }
}
