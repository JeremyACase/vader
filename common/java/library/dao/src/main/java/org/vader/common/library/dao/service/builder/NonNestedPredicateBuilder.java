package org.vader.common.library.dao.service.builder;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.vader.common.library.dao.model.QueryFilterParameter;
import org.vader.common.library.dao.model.QueryOperatorType;

/**
 * Builds JPA predicates for a direct (non-joined) field of the queried entity.
 */
@Service
public class NonNestedPredicateBuilder {

    /**
     * Builds a predicate for an {@link OffsetDateTime} field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperDate(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {
        return this.comparable(criteriaBuilder, path, parameter,
            OffsetDateTime.parse(parameter.getValue()));
    }

    /**
     * Builds a predicate for an {@link Integer} field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperInteger(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {
        return this.comparable(criteriaBuilder, path, parameter,
            Integer.valueOf(parameter.getValue()));
    }

    /**
     * Builds a predicate for a {@link Long} field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperLong(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {
        return this.comparable(criteriaBuilder, path, parameter,
            Long.valueOf(parameter.getValue()));
    }

    /**
     * Builds a predicate for a {@link Float} field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperFloat(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {
        return this.comparable(criteriaBuilder, path, parameter,
            Float.valueOf(parameter.getValue()));
    }

    /**
     * Builds a predicate for a {@link Double} field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperDouble(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {
        return this.comparable(criteriaBuilder, path, parameter,
            Double.valueOf(parameter.getValue()));
    }

    /**
     * Builds a predicate for a {@link String} field. Adds {@code LIKE} support on top of the
     * comparable operators.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperString(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {

        if (parameter.getOperator() == QueryOperatorType.LIKE) {
            return criteriaBuilder.like(path.get(parameter.getKey()), parameter.getValue());
        }
        return this.comparable(criteriaBuilder, path, parameter, parameter.getValue());
    }

    /**
     * Builds an {@code EQUAL} predicate for a {@link Boolean} field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param parameter the constraint
     * @return the predicate
     */
    public Predicate getPredicateHelperBoolean(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter) {

        requireEqual(parameter);
        return criteriaBuilder.equal(
            path.get(parameter.getKey()), Boolean.parseBoolean(parameter.getValue()));
    }

    /**
     * Builds an {@code EQUAL} predicate for an enum field.
     *
     * @param criteriaBuilder the criteria builder
     * @param path the path the field is reached from
     * @param field the reflected enum field
     * @param parameter the constraint
     * @return the predicate
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate getPredicateHelperEnum(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final Field field,
        final QueryFilterParameter parameter) {

        requireEqual(parameter);
        var value = Enum.valueOf((Class<Enum>) field.getType(),
            parameter.getValue().toUpperCase());
        return criteriaBuilder.equal(
            path.get(parameter.getKey()), field.getType().cast(value));
    }

    private <Y extends Comparable<? super Y>> Predicate comparable(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> path,
        final QueryFilterParameter parameter,
        final Y value) {

        final Path<Y> field = path.get(parameter.getKey());
        return switch (parameter.getOperator()) {
            case EQUAL -> criteriaBuilder.equal(field, value);
            case LESS_THAN -> criteriaBuilder.lessThan(field, value);
            case LESS_THAN_OR_EQUAL_TO -> criteriaBuilder.lessThanOrEqualTo(field, value);
            case GREATER_THAN -> criteriaBuilder.greaterThan(field, value);
            case GREATER_THAN_OR_EQUAL_TO -> criteriaBuilder.greaterThanOrEqualTo(field, value);
            default -> throw new IllegalArgumentException(
                "Unsupported operator " + parameter.getOperator() + " for field "
                    + parameter.getKey());
        };
    }

    private static void requireEqual(final QueryFilterParameter parameter) {
        if (parameter.getOperator() != QueryOperatorType.EQUAL) {
            throw new IllegalArgumentException(
                "Only EQUAL is supported for field " + parameter.getKey()
                    + " (got " + parameter.getOperator() + ")");
        }
    }
}
