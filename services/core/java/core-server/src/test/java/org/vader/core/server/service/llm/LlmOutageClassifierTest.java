package org.vader.core.server.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;

class LlmOutageClassifierTest {

    @Test
    void isOutage_forAnUnreachableLlm_isTrue() {
        assertThat(LlmOutageClassifier.isOutage(new OrchestratorUnavailableException(
            "Could not reach the local LLM.", new IllegalStateException("connection refused"))))
            .isTrue();
    }

    @Test
    void isOutage_forQueueTimeout_isTrue() {
        assertThat(LlmOutageClassifier.isOutage(
            new LlmRequestTimeoutException("the local LLM backend looks stuck")))
            .isTrue();
    }

    @Test
    void isOutage_findsAnOutageAnywhereInTheCauseChain() {
        var wrapped = new IllegalStateException("review failed",
            new RuntimeException("evaluation failed",
                new LlmRequestTimeoutException("the overall 1800s wait elapsed")));

        assertThat(LlmOutageClassifier.isOutage(wrapped)).isTrue();
    }

    @Test
    void isOutage_forRequestTheLlmProcessedButThatFailed_isFalse() {
        // e.g. the model's output could not be parsed -- retrying would just fail the same way.
        assertThat(LlmOutageClassifier.isOutage(
            new LlmRequestQueueException("LLM request abc failed: could not parse the response")))
            .isFalse();
    }

    @Test
    void isOutage_forAnUnrelatedFailure_isFalse() {
        assertThat(LlmOutageClassifier.isOutage(new IllegalStateException("bug"))).isFalse();
    }
}
