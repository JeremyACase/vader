/// Which participant produced a [`ConversationMessage`], mirroring core-server's
/// `ConversationRole`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConversationRole {
    System,
    User,
    Assistant,
    Tool,
}

/// One tool call the model requested (on an `Assistant` message) or the harness is reporting the
/// result of (on a `Tool` message).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    pub arguments_json: String,
}

/// One message in the running conversation sent to `core-server` on every inference turn. Which
/// fields are populated depends on `role` -- see core-server's `ConversationMessage` Javadoc for
/// the full mapping.
#[derive(Debug, Clone)]
pub struct ConversationMessage {
    pub role: ConversationRole,
    pub content: Option<String>,
    pub tool_calls: Vec<ToolCall>,
    pub tool_call_id: Option<String>,
    pub tool_name: Option<String>,
}

impl ConversationMessage {
    /// A `System` message: instructions for the model, with no tool calls.
    pub fn system(content: impl Into<String>) -> Self {
        Self::text(ConversationRole::System, content)
    }

    /// A `User` message: the objective or a follow-up, with no tool calls.
    pub fn user(content: impl Into<String>) -> Self {
        Self::text(ConversationRole::User, content)
    }

    /// An `Assistant` message replaying a prior turn's plain-text reply, so a follow-up `User`
    /// message (e.g. a nudge) has something to respond to.
    pub fn assistant_text(content: impl Into<String>) -> Self {
        Self::text(ConversationRole::Assistant, content)
    }

    /// An `Assistant` message replaying the tool calls a prior turn requested, so the following
    /// `Tool` messages have something to correlate against.
    pub fn assistant_tool_calls(tool_calls: Vec<ToolCall>) -> Self {
        Self {
            role: ConversationRole::Assistant,
            content: None,
            tool_calls,
            tool_call_id: None,
            tool_name: None,
        }
    }

    /// A `Tool` message: one tool call's result, folded back into the conversation.
    pub fn tool_result(
        tool_call_id: impl Into<String>,
        tool_name: impl Into<String>,
        result: impl Into<String>,
    ) -> Self {
        Self {
            role: ConversationRole::Tool,
            content: Some(result.into()),
            tool_calls: Vec::new(),
            tool_call_id: Some(tool_call_id.into()),
            tool_name: Some(tool_name.into()),
        }
    }

    fn text(role: ConversationRole, content: impl Into<String>) -> Self {
        Self {
            role,
            content: Some(content.into()),
            tool_calls: Vec::new(),
            tool_call_id: None,
            tool_name: None,
        }
    }
}

/// One model turn's result: either a final answer (`tool_calls` empty) or a request to call one
/// or more tools before the model can continue (`tool_calls` populated, `content` possibly
/// absent), plus how many tokens the turn cost against the assignment's budget and why the model
/// stopped generating (`finish_reason`, as the provider reported it -- absent if it reported none).
#[derive(Debug, Clone)]
pub struct InferenceTurn {
    pub content: Option<String>,
    pub tool_calls: Vec<ToolCall>,
    pub tokens_spent: u64,
    pub finish_reason: Option<String>,
}

/// The finish reason a provider reports when generation stopped at the output token cap rather
/// than ending naturally (Ollama's `done_reason`, relayed by core-server).
const OUTPUT_CAP_FINISH_REASON: &str = "length";

impl InferenceTurn {
    /// Whether the model was cut off at the output token cap. Whatever such a turn contains -- a
    /// half-written tool call, a truncated answer -- is incomplete, however plausible it looks.
    pub fn was_cut_off(&self) -> bool {
        self.finish_reason.as_deref() == Some(OUTPUT_CAP_FINISH_REASON)
    }

    /// The stall detector's fingerprint for this turn: the tool call(s) it requested (name +
    /// arguments), or its final text when it made none. Two turns that both call the same tool
    /// with the same arguments -- or both answer with the same text -- fingerprint identically.
    pub fn fingerprint(&self) -> String {
        if self.tool_calls.is_empty() {
            self.content.clone().unwrap_or_default()
        } else {
            self.tool_calls
                .iter()
                .map(|call| format!("{}({})", call.name, call.arguments_json))
                .collect::<Vec<_>>()
                .join(";")
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn turn(content: Option<&str>, tool_calls: Vec<ToolCall>) -> InferenceTurn {
        InferenceTurn {
            content: content.map(str::to_string),
            tool_calls,
            tokens_spent: 1,
            finish_reason: Some("stop".to_string()),
        }
    }

    fn tool_call(name: &str, arguments_json: &str) -> ToolCall {
        ToolCall {
            id: "call-1".to_string(),
            name: name.to_string(),
            arguments_json: arguments_json.to_string(),
        }
    }

    #[test]
    fn a_text_turn_fingerprints_as_its_text() {
        assert_eq!(turn(Some("done"), Vec::new()).fingerprint(), "done");
    }

    #[test]
    fn a_tool_turn_fingerprints_as_its_calls_and_ignores_its_text() {
        let calls = vec![tool_call("a", "{}"), tool_call("b", "{\"x\":1}")];
        assert_eq!(
            turn(Some("thinking"), calls).fingerprint(),
            "a({});b({\"x\":1})"
        );
    }
}
