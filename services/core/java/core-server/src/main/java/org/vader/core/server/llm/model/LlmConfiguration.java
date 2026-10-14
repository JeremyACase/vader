package org.vader.core.server.llm.model;

import java.util.Map;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.config.VaderMode;

/**
 * Everything that decides what the LLM is asked and how it samples: two benchmark runs are only
 * comparable when this is identical, so a runner records it alongside every result.
 *
 * @param mode the deployment's mode
 * @param model the model every call goes to, or the orchestrator type when no local model does
 * @param temperature the sampling temperature, if configured
 * @param seed the sampling seed, if configured
 * @param contextTokens the context window per call, if configured
 * @param maxOutputTokens the generation cap per call, if configured
 * @param promptFingerprints each call kind's prompt-template fingerprint
 */
public record LlmConfiguration(
    VaderMode mode,
    String model,
    Double temperature,
    Integer seed,
    Integer contextTokens,
    Integer maxOutputTokens,
    Map<LlmRequestKind, String> promptFingerprints) {
}
