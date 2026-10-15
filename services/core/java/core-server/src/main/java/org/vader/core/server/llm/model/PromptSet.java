package org.vader.core.server.llm.model;

import java.util.Map;

/**
 * One call kind's prompts: the templates its calls are built from and the fingerprint that names
 * that exact wording, so a benchmark result can be read next to the prompts that produced it.
 *
 * @param fingerprint the call kind's prompt fingerprint
 * @param templates each template's text by its name, e.g. {@code review/evaluation-system}
 */
public record PromptSet(String fingerprint, Map<String, String> templates) {
}
