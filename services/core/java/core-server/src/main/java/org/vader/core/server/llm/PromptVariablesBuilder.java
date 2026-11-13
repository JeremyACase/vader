package org.vader.core.server.llm;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects the variables for one {@link PromptTemplateRegistry#render} call. Unlike
 * {@link Map#of}, accepts {@code null}: a template tests an optional section with
 * {@code <if(name)>}, so leaving a value out is how a caller omits that section.
 */
public class PromptVariablesBuilder {

    private final Map<String, Object> variables = new HashMap<>();

    /**
     * Adds one variable.
     *
     * @param name the variable's name in the template
     * @param value its value, or {@code null} for an absent optional section
     * @return this builder
     */
    public PromptVariablesBuilder with(final String name, final Object value) {
        this.variables.put(name, value);
        return this;
    }

    /**
     * The collected variables.
     *
     * @return a copy, so later {@link #with} calls don't change it
     */
    public Map<String, Object> build() {
        return new HashMap<>(this.variables);
    }
}
