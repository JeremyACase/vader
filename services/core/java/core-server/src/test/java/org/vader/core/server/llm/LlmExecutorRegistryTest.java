package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.orchestration.DecompositionLlmExecutor;
import org.vader.core.server.review.EvaluationLlmExecutor;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.EvaluationVerdict;
import org.vader.core.server.taskagent.InferenceTurnLlmExecutor;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.InferenceTurn;

class LlmExecutorRegistryTest {

    private final EvaluationLlmExecutor evaluationExecutor = new EvaluationLlmExecutor();
    private final InferenceTurnLlmExecutor inferenceTurnExecutor = new InferenceTurnLlmExecutor();
    private LlmExecutorRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<InterfaceLlmExecutor<?, ?>> executors = mock(ObjectProvider.class);
        when(executors.orderedStream()).thenAnswer(
            call -> Stream.of(this.evaluationExecutor, this.inferenceTurnExecutor));
        this.registry = new LlmExecutorRegistry();
        ReflectionTestUtils.setField(this.registry, "executors", executors);
    }

    @Test
    void forType_findsTheExecutorOfThatClass() {
        assertThat(this.registry.forType(EvaluationLlmExecutor.class))
            .isSameAs(this.evaluationExecutor);
    }

    @Test
    void forType_whenNoneIsRegistered_throws() {
        assertThatThrownBy(() -> this.registry.forType(DecompositionLlmExecutor.class))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("DecompositionLlmExecutor");
    }

    @Test
    void forKind_findsTheExecutorForThatKind() {
        assertThat(this.registry.forKind(LlmRequestKind.INFERENCE_TURN))
            .isSameAs(this.inferenceTurnExecutor);
    }

    @Test
    void forKind_whenNoneIsRegistered_throws() {
        assertThatThrownBy(() -> this.registry.forKind(LlmRequestKind.DECOMPOSITION))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("DECOMPOSITION");
    }

    @Test
    void typeArguments_resolveTheExecutorsRequestAndResponseTypes() {
        assertThat(LlmExecutorRegistry.requestTypeOf(this.evaluationExecutor))
            .isEqualTo(EvaluationRequest.class);
        assertThat(LlmExecutorRegistry.responseTypeOf(this.evaluationExecutor))
            .isEqualTo(EvaluationVerdict.class);
        assertThat(LlmExecutorRegistry.responseTypeOf(this.inferenceTurnExecutor))
            .isEqualTo(InferenceTurn.class);
    }

    @Test
    void requestTypeOf_keepsGenericTypeArgumentsForDecoding() {
        var type = new ObjectMapper().constructType(
            LlmExecutorRegistry.requestTypeOf(this.inferenceTurnExecutor));

        assertThat(type.getRawClass()).isEqualTo(List.class);
        assertThat(type.getContentType().getRawClass()).isEqualTo(ConversationMessage.class);
    }

    @Test
    void typeArguments_resolveThroughSubclassesSuchAsMocksOrProxies() {
        var mockExecutor = mock(EvaluationLlmExecutor.class);

        assertThat(LlmExecutorRegistry.responseTypeOf(mockExecutor))
            .isEqualTo(EvaluationVerdict.class);
    }
}
