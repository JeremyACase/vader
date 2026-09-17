package org.vader.core.server.service.strategies.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;

class StaticInferenceGatewayStrategyTest {

    private McpToolCallbackRegistry toolCallbackRegistry;
    private StaticInferenceGatewayStrategy strategy;

    @BeforeEach
    void setUp() {
        this.toolCallbackRegistry = mock(McpToolCallbackRegistry.class);
        when(this.toolCallbackRegistry.all()).thenReturn(List.of());
        this.strategy = new StaticInferenceGatewayStrategy();
        ReflectionTestUtils.setField(
            this.strategy, "toolCallbackRegistry", this.toolCallbackRegistry);
    }

    @Test
    void complete_onTheFirstTurn_scriptsToolCallInsteadOfAnsweringDirectly() {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "do the task", null, null, null));

        var turn = this.strategy.complete(messages);

        assertThat(turn.content()).isNull();
        assertThat(turn.toolCalls()).hasSize(1);
        assertThat(turn.toolCalls().get(0).name()).isEqualTo("list_queryable_entities");
        assertThat(turn.toolCalls().get(0).argumentsJson()).isEqualTo("{}");
    }

    @Test
    void complete_onceToolResultIsInTheConversation_returnsTheCannedFinalAnswer() {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "do the task", null, null, null),
            new ConversationMessage(
                ConversationRole.TOOL, "[]", null, "static-scripted-call-1",
                "list_queryable_entities"));

        var turn = this.strategy.complete(messages);

        assertThat(turn.content()).contains("Static inference response");
        assertThat(turn.toolCalls()).isEmpty();
        assertThat(turn.tokensSpent()).isEqualTo(10L);
    }
}
