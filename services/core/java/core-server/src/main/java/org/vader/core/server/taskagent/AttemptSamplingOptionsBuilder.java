package org.vader.core.server.taskagent;

import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Builds the sampling options for one attempt's inference turns when the model pins a seed (EVAL
 * mode). With one seed for every call, a retry whose prompt barely differs from the failed
 * attempt's samples the same reply and repeats its mistakes, so each attempt offsets the seed by
 * its number: attempt 1 keeps the configured seed, and every later attempt's seed is still fixed
 * by its number, keeping a benchmark run repeatable. Without a pinned seed every call already
 * samples afresh, and no options are built.
 *
 * <p>Starts from a copy of the model's defaults rather than naming the seed alone, because the
 * Ollama model replaces its default options with whatever a request carries instead of merging
 * the two -- a seed-only request would lose the model name, temperature and context size.</p>
 */
@Component
public class AttemptSamplingOptionsBuilder {

    @Autowired
    private ChatModel chatModel;

    /**
     * Builds the options for one attempt.
     *
     * @param attemptNumber the attempt's number, starting at 1 ({@code null} if unknown)
     * @return the model's defaults with the attempt's seed, or empty when no seed is pinned or the
     *     attempt is unknown
     */
    public Optional<ChatOptions.Builder<?>> forAttempt(final Integer attemptNumber) {
        return Optional.ofNullable(attemptNumber)
            .flatMap(number -> seededDefaults(this.chatModel.getOptions())
                .map(defaults -> defaults.mutate().seed(defaults.getSeed() + number - 1)));
    }

    private static Optional<OllamaChatOptions> seededDefaults(final ChatOptions options) {
        return options instanceof OllamaChatOptions ollama && Objects.nonNull(ollama.getSeed())
            ? Optional.of(ollama)
            : Optional.empty();
    }
}
