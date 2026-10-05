package org.vader.core.server.orchestration.model;

import java.util.List;

/**
 * One decomposition request, as queued for the LLM.
 *
 * <p>Revision guidance is its own field, never spliced into the user's text, so the model sees the
 * request exactly as written and the guidance arrives as a system instruction. Spliced in, a
 * model tends to copy the critique into its new plan's reasoning.</p>
 *
 * @param clientPromptText the user's original request, verbatim
 * @param attachedFiles the files attached to the request, by name and type only
 * @param revisionGuidance why the previous plan for this same request was rejected, or
 *     {@code null} on the first attempt
 */
public record DecompositionRequest(
    String clientPromptText, List<AttachedFile> attachedFiles, String revisionGuidance) {
}
