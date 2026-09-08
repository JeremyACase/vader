package org.vader.common.library.dao.service.builder;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.vader.common.library.dao.model.QueryFilterParameter;
import org.vader.common.library.dao.model.QueryOperatorType;

/**
 * Builds JPA predicates for a field reached through one or more joins, wiring the constraint into
 * a correlated {@link Subquery} and wrapping it in {@code EXISTS}.
 */
@Service
public class NestedPredicateBuilder {

    /**
     * Builds a nested predicate from a structured filter parameter.
     *
     * @param parameter the constraint (operator + value)
     * @param criteriaBuilder the criteria builder
     * @param path the joined path to the field
     * @param subQuery the subquery to attach the constraint to
     * @return an {@code EXISTS} predicate over the subquery
     */
    public Predicate getNestedQueryPredicate(
        final QueryFilterParameter parameter,
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final Subquery<?> subQuery) {

        var value = parseValue(parameter.getValue(), path.getJavaType());
        return buildNestedPredicate(
            criteriaBuilder, path, subQuery, parameter.getOperator(), value);
    }

    /**
     * Builds a nested predicate from a raw keychain, value, and expected type (the request-param
     * path, where the operator is encoded as a trailing symbol on the keychain).
     *
     * @param keychain the dotted keychain, optionally suffixed with {@code <}, {@code >} or
     *     {@code *}
     * @param criteriaBuilder the criteria builder
     * @param path the joined path to the field
     * @param subQuery the subquery to attach the constraint to
     * @param queryValue the raw string value
     * @param type the expected Java type of the field
     * @return an {@code EXISTS} predicate over the subquery
     */
    public Predicate getNestedQueryPredicate(
        final String keychain,
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final Subquery<?> subQuery,
        final String queryValue,
        final Class<?> type) {

        var value = parseValue(queryValue, type);
        return buildNestedPredicate(
            criteriaBuilder, path, subQuery, operatorFromKeychain(keychain), value);
    }

    /**
     * Builds a nested predicate for an enum field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the joined path to the enum field
     * @param subQuery the subquery to attach the constraint to
     * @param field the reflected enum field
     * @param queryValue the raw string value (or {@code null} / {@code !null})
     * @return an {@code EXISTS} predicate over the subquery
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate getNestedQueryPredicateEnum(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final Subquery<?> subQuery,
        final Field field,
        final String queryValue) {

        if ("!null".equalsIgnoreCase(queryValue)) {
            subQuery.where(criteriaBuilder.isNotNull(path));
        } else if ("null".equalsIgnoreCase(queryValue)) {
            subQuery.where(criteriaBuilder.isNull(path));
        } else {
            var value = Enum.valueOf((Class<Enum>) field.getType(), queryValue.toUpperCase());
            subQuery.where(criteriaBuilder.equal(path, field.getType().cast(value)));
        }
        return criteriaBuilder.exists(subQuery);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate buildNestedPredicate(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final Subquery<?> subQuery,
        final QueryOperatorType operator,
        final Object value) {

        if (Objects.isNull(value)) {
            subQuery.where(criteriaBuilder.isNull(path));
        } else if ("!null".equalsIgnoreCase(String.valueOf(value))) {
            subQuery.where(criteriaBuilder.isNotNull(path));
        } else {
            var expression = (Path<Comparable>) path;
            var comparable = (Comparable) value;
            switch (operator) {
                case EQUAL -> subQuery.where(criteriaBuilder.equal(path, value));
                case LIKE -> subQuery.where(
                    criteriaBuilder.like((Path<String>) path, String.valueOf(value)));
                case GREATER_THAN -> subQuery.where(
                    criteriaBuilder.greaterThan(expression, comparable));
                case GREATER_THAN_OR_EQUAL_TO -> subQuery.where(
                    criteriaBuilder.greaterThanOrEqualTo(expression, comparable));
                case LESS_THAN -> subQuery.where(
                    criteriaBuilder.lessThan(expression, comparable));
                case LESS_THAN_OR_EQUAL_TO -> subQuery.where(
                    criteriaBuilder.lessThanOrEqualTo(expression, comparable));
                default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
        }
        return criteriaBuilder.exists(subQuery);
    }

    private static QueryOperatorType operatorFromKeychain(final String keychain) {
        if (keychain.endsWith("<")) {
            return QueryOperatorType.LESS_THAN;
        }
        if (keychain.endsWith(">")) {
            return QueryOperatorType.GREATER_THAN;
        }
        if (keychain.endsWith("*")) {
            return QueryOperatorType.LIKE;
        }
        return QueryOperatorType.EQUAL;
    }

    private static Object parseValue(final String value, final Class<?> type) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (type.equals(OffsetDateTime.class)) {
            return OffsetDateTime.parse(value);
        }
        if (type.equals(Integer.class)) {
            return Integer.valueOf(value);
        }
        if (type.equals(Long.class)) {
            return Long.valueOf(value);
        }
        if (type.equals(Float.class)) {
            return Float.valueOf(value);
        }
        if (type.equals(Double.class)) {
            return Double.valueOf(value);
        }
        if (type.equals(Boolean.class)) {
            return Boolean.valueOf(value);
        }
        return value;
    }
}
