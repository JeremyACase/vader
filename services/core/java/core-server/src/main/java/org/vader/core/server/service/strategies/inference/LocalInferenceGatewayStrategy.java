package org.vader.core.server.service.strategies.inference;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.InferenceTurn;

/**
 * Completes a turn against the in-cluster Ollama instance via Spring AI's {@link ChatClient}.
 * Active only when {@code vader.orchestrator.type} is {@code local}.
 *
 * <p>Unlike the decomposition orchestrator, this issues no tool calls and expects no structured
 * output -- a harness's own action loop lives in the harness process, not here. This is
 * deliberately the only place in {@code core-server} (besides the decomposition orchestrator)
 * that ever calls an LLM directly; the harness reaches it exclusively through
 * {@code /vader/core-server/agent/inference}.</p>
 *
 * <p>Two Spring AI pitfalls had to be avoided here, both of which manifest as
 * {@code IllegalStateException: No CallAdvisors available to execute}:</p>
 * <ul>
 * <li>Calling a second terminal method (e.g. {@code chatResponse()}) on a
 * {@code CallResponseSpec} after already calling another one (e.g. {@code content()}) re-runs
 * the same, already-consumed advisor chain and fails deterministically, every time -- not
 * intermittently. Both the content and the token usage are read off a single
 * {@link ChatResponse} from one {@code chatResponse()} call instead.</li>
 * <li>A fresh {@link ChatClient} is still built per call rather than cached on the bean:
 * separately, concurrent {@code .call()} invocations against one shared instance can corrupt
 * Spring AI's internal advisor-chain state (spring-projects/spring-ai#3537, still open as of
 * 1.0.9). Building from {@link ChatClient.Builder} is cheap (it wraps an already-configured
 * {@code ChatModel}, no new network connection), so there is no real cost to paying it per call
 * instead of once at startup.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalInferenceGatewayStrategy implements InterfaceInferenceGatewayStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalInferenceGatewayStrategy.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public InferenceTurn complete(final String prompt) {
        try {
            var chatResponse =
                this.chatClientBuilder.build().prompt().user(prompt).call().chatResponse();
            return new InferenceTurn(this.contentOf(chatResponse), this.tokensSpent(chatResponse));
        } catch (RuntimeException e) {
            logger.warn("Local LLM inference call failed: {}", e.getMessage());
            throw new OrchestratorUnavailableException("Could not reach the local LLM.", e);
        }
    }

    private String contentOf(final ChatResponse chatResponse) {
        var result = Objects.isNull(chatResponse) ? null : chatResponse.getResult();
        return Objects.isNull(result) ? null : result.getOutput().getText();
    }

    private long tokensSpent(final ChatResponse chatResponse) {
        if (Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getMetadata())) {
            return 0L;
        }
        var usage = chatResponse.getMetadata().getUsage();
        if (Objects.isNull(usage) || Objects.isNull(usage.getTotalTokens())) {
            return 0L;
        }
        return usage.getTotalTokens();
    }
}
