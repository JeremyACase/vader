package org.vader.common.library.dao.model;

import jakarta.validation.constraints.NotNull;

/**
 * A single constraint within a {@link QueryFilter}: apply {@code operator} between the field named
 * by {@code key} and {@code value}.
 *
 * <p>{@code key} may be a dotted keychain to reach an associated entity, e.g.
 * {@code "taskPlan.objective"}. A {@code value} of {@code "null"} / {@code "!null"} (any case)
 * tests for {@code IS NULL} / {@code IS NOT NULL} regardless of {@code operator}.</p>
 */
public class QueryFilterParameter {

    @NotNull
    private String key = "id";

    @NotNull
    private String value;

    @NotNull
    private QueryOperatorType operator = QueryOperatorType.EQUAL;

    /**
     * Returns the field name (or dotted keychain) to constrain.
     *
     * @return the key
     */
    public String getKey() {
        return this.key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    /**
     * Returns the value to compare against, as a string (parsed to the field's type at query
     * time).
     *
     * @return the value
     */
    public String getValue() {
        return this.value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    /**
     * Returns the comparison operator.
     *
     * @return the operator
     */
    public QueryOperatorType getOperator() {
        return this.operator;
    }

    public void setOperator(final QueryOperatorType operator) {
        this.operator = operator;
    }
}
