package org.vader.core.server.service.strategies.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.llm.LlmRequestQueue;

class LocalInferenceGatewayStrategyTest {

    private static List<ConversationMessage> userTurn(final String text) {
        return List.of(new ConversationMessage(ConversationRole.USER, text, null, null, null));
    }

    private static LocalInferenceGatewayStrategy strategyWith(final LlmRequestQueue requestQueue) {
        var strategy = new LocalInferenceGatewayStrategy();
        ReflectionTestUtils.setField(strategy, "requestQueue", requestQueue);
        return strategy;
    }

    @Test
    void complete_delegatesToTheRequestQueueAndReturnsItsResult() {
        var requestQueue = mock(LlmRequestQueue.class);
        var messages = userTurn("hi");
        var turn = new InferenceTurn("hello there", List.of(), 7L, "stop");
        when(requestQueue.submitInferenceTurn(messages)).thenReturn(turn);

        var result = strategyWith(requestQueue).complete(messages);

        assertThat(result).isSameAs(turn);
    }

    @Test
    void complete_whenTheQueueThrows_wrapsItAsOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        var messages = userTurn("hi");
        when(requestQueue.submitInferenceTurn(messages))
            .thenThrow(new RuntimeException("no replica ever processed it"));

        assertThatThrownBy(() -> strategyWith(requestQueue).complete(messages))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }
}
