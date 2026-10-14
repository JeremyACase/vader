package org.vader.core.server.taskagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vader.core.server.taskagent.model.TurnUsage;

/**
 * Warns when an inference turn's prompt came close to the context window. Ollama silently drops
 * the oldest messages from a prompt over {@code num-ctx} minus {@code num-predict} tokens, so a
 * long-running agent can lose the opening message that tells it its task with no error at all;
 * this makes that visible.
 *
 * <p>Takes the larger of the provider's prompt token count, which can leave out a cached prefix,
 * and an estimate from the characters sent, at a conservative three characters per token.</p>
 */
@Component
public class PromptBudgetMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PromptBudgetMonitor.class);

    private static final long CHARACTERS_PER_TOKEN = 3L;

    private static final double WARNING_FRACTION = 0.9;

    @Value("${spring.ai.ollama.chat.options.num-ctx:#{null}}")
    private Integer contextTokens;

    @Value("${spring.ai.ollama.chat.options.num-predict:#{null}}")
    private Integer maxOutputTokens;

    /**
     * Logs a warning when the turn's prompt is within reach of the context window.
     *
     * @param assignmentId the attempt the turn belongs to
     * @param turnIndex the turn's position in the attempt
     * @param usage what the turn used
     * @return whether the prompt came near the window; always {@code false} when no window is
     *     configured
     */
    public boolean warnIfNearLimit(
            final String assignmentId, final int turnIndex, final TurnUsage usage) {
        var budget = this.promptBudget();
        var estimate =
            Math.max(usage.promptTokens(), usage.promptCharacters() / CHARACTERS_PER_TOKEN);
        var nearLimit = budget > 0 && estimate >= budget * WARNING_FRACTION;
        if (nearLimit) {
            logger.warn("Attempt {} turn {}: prompt of about {} tokens is near the {}-token prompt "
                + "budget; Ollama drops the oldest messages past it", assignmentId, turnIndex,
                estimate, budget);
        }
        return nearLimit;
    }

    private long promptBudget() {
        return this.contextTokens == null || this.maxOutputTokens == null
            ? 0L
            : (long) this.contextTokens - this.maxOutputTokens;
    }
}
