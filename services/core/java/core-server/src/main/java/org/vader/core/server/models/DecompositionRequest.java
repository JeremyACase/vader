package org.vader.core.server.models;

/**
 * One decomposition request, as queued for the LLM.
 *
 * <p>Revision guidance travels as its own field -- never spliced into the user's request text --
 * so the model always sees the user's words exactly as written, and the guidance can be framed as
 * instructions from the system rather than something the user said. Splicing it into the request
 * text had a small model copy a previous plan's critique verbatim into its new plan's
 * {@code reasoning}, describing tasks the new plan didn't even contain.</p>
 *
 * @param clientPromptText the user's original request, verbatim
 * @param revisionGuidance why the previous plan for this same request was rejected, or
 *     {@code null} on the first attempt
 */
public record DecompositionRequest(String clientPromptText, String revisionGuidance) {
}
