package org.vader.common.library.dao.model;

/**
 * The comparison operators a {@link QueryFilterParameter} can apply to a field.
 */
public enum QueryOperatorType {

    /** {@code field < value}. */
    LESS_THAN,

    /** {@code field <= value}. */
    LESS_THAN_OR_EQUAL_TO,

    /** {@code field > value}. */
    GREATER_THAN,

    /** {@code field >= value}. */
    GREATER_THAN_OR_EQUAL_TO,

    /** SQL {@code LIKE} — {@code value} may contain {@code %} / {@code _} wildcards. */
    LIKE,

    /** {@code field = value}. */
    EQUAL
}
