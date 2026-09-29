package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.OrchestratorUnavailableException;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceTurn;

class InferenceGatewayTest {

    private static List<ConversationMessage> userTurn(final String text) {
        return List.of(new ConversationMessage(ConversationRole.USER, text, null, null, null));
    }

    private static InferenceGateway gatewayWith(final LlmRequestQueue requestQueue) {
        var gateway = new InferenceGateway();
        ReflectionTestUtils.setField(gateway, "requestQueue", requestQueue);
        return gateway;
    }

    @Test
    void complete_delegatesToTheRequestQueueAndReturnsItsResult() {
        var requestQueue = mock(LlmRequestQueue.class);
        var messages = userTurn("hi");
        var turn = new InferenceTurn("hello there", List.of(), 7L, "stop");
        when(requestQueue.submit(InferenceTurnLlmExecutor.class, messages)).thenReturn(turn);

        var result = gatewayWith(requestQueue).complete(messages);

        assertThat(result).isSameAs(turn);
    }

    @Test
    void complete_whenTheQueueThrows_wrapsItAsOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        var messages = userTurn("hi");
        when(requestQueue.submit(InferenceTurnLlmExecutor.class, messages))
            .thenThrow(new RuntimeException("no replica ever processed it"));

        assertThatThrownBy(() -> gatewayWith(requestQueue).complete(messages))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }
}
