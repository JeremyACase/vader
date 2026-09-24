package org.vader.core.server.service.llm;

import java.util.Objects;
import java.util.stream.Stream;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;

/**
 * Decides whether a failure means "the LLM is unavailable right now" -- worth waiting out and
 * retrying -- as opposed to anything else, which retrying would most likely just repeat.
 *
 * <p>An outage is either the LLM being unreachable ({@link OrchestratorUnavailableException}, which
 * every {@code Local*Strategy} throws for an unreachable backend) or a caller giving up on the LLM
 * queue ({@link LlmRequestTimeoutException}). A request the LLM did process but that failed --
 * e.g. output that could not be parsed -- is deliberately not an outage: at temperature 0 the
 * same request would fail the same way on every retry, forever.</p>
 */
public final class LlmOutageClassifier {

    // Bounds the cause-chain walk against a pathological self-referencing chain.
    private static final int MAX_CAUSE_DEPTH = 20;

    private LlmOutageClassifier() {
    }

    /**
     * Whether {@code failure}, or anything in its cause chain, signals an LLM outage.
     *
     * @param failure the failure to classify
     * @return {@code true} if the LLM is unavailable and the work is worth retrying later
     */
    public static boolean isOutage(final Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, Throwable::getCause)
            .limit(MAX_CAUSE_DEPTH)
            .anyMatch(cause -> cause instanceof OrchestratorUnavailableException
                || cause instanceof LlmRequestTimeoutException);
    }
}
