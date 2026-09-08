package org.vader.common.library.dao.component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.vader.common.library.dao.model.GenericPageImplementation;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.library.dao.model.QueryFilterParameter;
import org.vader.common.library.dao.model.SortType;
import org.vader.common.library.dao.service.builder.NestedPredicateBuilder;
import org.vader.common.library.dao.service.builder.NonNestedPredicateBuilder;
import org.vader.common.library.dao.service.logic.ClassDeriver;
import org.vader.common.library.dao.service.logic.EntityDeriver;

/**
 * Queries an entity type from the database given a structured {@link QueryFilter}: an
 * {@code AND}-combined list of field constraints, supporting dotted keychains that join through
 * associations.
 *
 * @param <T> the entity type
 */
@Component
public class FilterDao<T> {

    private static final Logger logger = LoggerFactory.getLogger(FilterDao.class);

    @Autowired
    private ClassDeriver classDeriver;

    @Autowired
    private EntityDeriver entityDeriver;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NestedPredicateBuilder nestedPredicateBuilder;

    @Autowired
    private NonNestedPredicateBuilder nonNestedPredicateBuilder;

    /**
     * Returns a page of entities matching {@code queryFilter}.
     *
     * @param queryFilter the constraints
     * @param page the zero-based page number
     * @param size the page size
     * @param clazz the entity class
     * @return the matching page
     * @throws NoSuchFieldException if the filter references a field that does not exist
     */
    @Transactional
    public Page<T> getPage(
        final QueryFilter queryFilter,
        final int page,
        final int size,
        final Class<T> clazz) throws NoSuchFieldException {

        var predicateClass = this.classDeriver.tryGetPredicateClass(clazz, queryFilter);

        var criteriaBuilder = this.entityManager.getCriteriaBuilder();
        var criteriaQuery = criteriaBuilder.createQuery(predicateClass);
        var root = criteriaQuery.from(predicateClass);

        this.trySetOrderBy(queryFilter, criteriaBuilder, root, criteriaQuery);
        var pageRequest = this.getPageRequest(queryFilter, page, size);

        return this.queryDatabase(
            queryFilter, criteriaBuilder, criteriaQuery, root, pageRequest, predicateClass);
    }

    /**
     * Returns the number of entities matching {@code queryFilter}.
     *
     * @param queryFilter the constraints
     * @param clazz the entity class
     * @return the match count
     * @throws NoSuchFieldException if the filter references a field that does not exist
     */
    public Long getCount(
        final QueryFilter queryFilter,
        final Class<?> clazz) throws NoSuchFieldException {

        var criteriaBuilder = this.entityManager.getCriteriaBuilder();
        var countQuery = criteriaBuilder.createQuery(Long.class);
        var countRoot = countQuery.from(clazz);

        var predicates = this.getPredicates(
            queryFilter, criteriaBuilder, countQuery, countRoot, clazz);

        countQuery
            .select(criteriaBuilder.count(countRoot))
            .where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));

        return this.entityManager.createQuery(countQuery).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    private Page<T> queryDatabase(
        final QueryFilter queryFilter,
        final CriteriaBuilder criteriaBuilder,
        final CriteriaQuery<?> criteriaQuery,
        final Root<?> root,
        final PageRequest pageRequest,
        final Class<?> clazz) throws NoSuchFieldException {

        var predicateClass = this.classDeriver.tryGetPredicateClass(clazz, queryFilter);
        var predicates = this.getPredicates(
            queryFilter, criteriaBuilder, criteriaQuery, root, predicateClass);

        criteriaQuery.where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));

        var listSize = Math.max(pageRequest.getPageSize(), 1);
        var records = this.entityManager.createQuery(criteriaQuery)
            .setFirstResult(pageRequest.getPageNumber() * pageRequest.getPageSize())
            .setHint("org.hibernate.cacheable", true)
            .setMaxResults(listSize)
            .getResultList();

        var count = this.getCount(queryFilter, clazz);
        return (GenericPageImplementation<T>) new GenericPageImplementation<>(
            records, pageRequest, count);
    }

    private List<Predicate> getPredicates(
        final QueryFilter filter,
        final CriteriaBuilder criteriaBuilder,
        final CriteriaQuery<?> criteriaQuery,
        final Root<?> root,
        final Class<?> clazz) throws NoSuchFieldException {

        var predicates = new ArrayList<Predicate>();
        for (var filterParameter : filter.getParameters()) {
            var split = Arrays.asList(filterParameter.getKey().split("\\."));
            if (split.size() > 1) {
                predicates.add(this.getPredicateForNestedParameter(
                    criteriaBuilder, criteriaQuery, root, filterParameter, split, clazz));
            } else {
                predicates.add(this.getPredicateForParameter(
                    criteriaBuilder, filterParameter, root, clazz));
            }
        }
        return predicates;
    }

    private Predicate getPredicateForParameter(
        final CriteriaBuilder criteriaBuilder,
        final QueryFilterParameter filterParameter,
        final Root<?> root,
        final Class<?> clazz) {

        var match = FieldUtils.getAllFieldsList(clazz).stream()
            .filter(field -> field.getName().equalsIgnoreCase(filterParameter.getKey()))
            .findFirst();
        if (match.isEmpty()) {
            return null;
        }
        return this.getPredicateForParameterHelper(
            criteriaBuilder, match.get(), root, filterParameter);
    }

    private Predicate getPredicateForNestedParameter(
        final CriteriaBuilder criteriaBuilder,
        final CriteriaQuery<?> criteriaQuery,
        final Root<?> root,
        final QueryFilterParameter filterParameter,
        final List<String> split,
        final Class<?> clazz) throws NoSuchFieldException {

        var currentClass = clazz;
        Field finalField = null;

        for (var segment : split) {
            var cleanSegment = stripOperatorSymbols(segment);
            var field = findFieldIgnoreCase(currentClass, cleanSegment);
            if (Objects.isNull(field)) {
                logger.debug("Field '{}' not on '{}', checking subclasses...",
                    cleanSegment, currentClass.getSimpleName());
                currentClass = this.classDeriver.tryGetPredicateClass(currentClass, cleanSegment);
                field = findFieldIgnoreCase(currentClass, cleanSegment);
                if (Objects.isNull(field)) {
                    throw new NoSuchFieldException("Field '" + cleanSegment + "' not found in "
                        + currentClass.getSimpleName() + " or its subclasses.");
                }
            }
            finalField = field;
            if (Collection.class.isAssignableFrom(field.getType())) {
                var collectionType = (ParameterizedType) field.getGenericType();
                currentClass = (Class<?>) collectionType.getActualTypeArguments()[0];
            } else {
                currentClass = field.getType();
            }
        }

        var subQuery = criteriaQuery.subquery(currentClass);
        Root<?> subRoot;
        if (this.entityDeriver.isEntityClass(currentClass)) {
            subRoot = subQuery.from(currentClass);
        } else {
            subRoot = subQuery.correlate(root);
        }

        var join = subRoot.join(stripOperatorSymbols(split.get(0)));
        for (int i = 1; i < split.size() - 1; i++) {
            join = join.join(stripOperatorSymbols(split.get(i)));
        }
        var joinedPath = join.get(stripOperatorSymbols(split.get(split.size() - 1)));

        return this.getPredicateForNestedParameterHelper(
            filterParameter, criteriaBuilder, joinedPath, subQuery, finalField);
    }

    private Predicate getPredicateForParameterHelper(
        final CriteriaBuilder criteriaBuilder,
        final Field field,
        final Root<?> root,
        final QueryFilterParameter parameter) {

        if (parameter.getValue().equalsIgnoreCase("null")) {
            return criteriaBuilder.isNull(root.get(parameter.getKey()));
        }
        if (parameter.getValue().equalsIgnoreCase("!null")) {
            return criteriaBuilder.isNotNull(root.get(parameter.getKey()));
        }

        var type = field.getType();
        if (type.equals(OffsetDateTime.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperDate(
                criteriaBuilder, root, parameter);
        }
        if (type.equals(String.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperString(
                criteriaBuilder, root, parameter);
        }
        if (type.equals(Boolean.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperBoolean(
                criteriaBuilder, root, parameter);
        }
        if (type.equals(Integer.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperInteger(
                criteriaBuilder, root, parameter);
        }
        if (type.equals(Long.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperLong(
                criteriaBuilder, root, parameter);
        }
        if (type.equals(Float.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperFloat(
                criteriaBuilder, root, parameter);
        }
        if (type.equals(Double.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperDouble(
                criteriaBuilder, root, parameter);
        }
        if (type.isEnum()) {
            return this.nonNestedPredicateBuilder.getPredicateHelperEnum(
                criteriaBuilder, root, field, parameter);
        }
        throw new IllegalArgumentException("Unsupported field type " + type.getSimpleName()
            + " for field " + parameter.getKey());
    }

    private Predicate getPredicateForNestedParameterHelper(
        final QueryFilterParameter filterParameter,
        final CriteriaBuilder criteriaBuilder,
        final Path<?> joinedPath,
        final Subquery<?> subQuery,
        final Field finalField) {

        if (finalField.getType().isEnum()) {
            return this.nestedPredicateBuilder.getNestedQueryPredicateEnum(
                criteriaBuilder, joinedPath, subQuery, finalField, filterParameter.getValue());
        }
        return this.nestedPredicateBuilder.getNestedQueryPredicate(
            filterParameter, criteriaBuilder, joinedPath, subQuery);
    }

    private static Field findFieldIgnoreCase(final Class<?> clazz, final String fieldName) {
        return FieldUtils.getAllFieldsList(clazz).stream()
            .filter(field -> field.getName().equalsIgnoreCase(fieldName))
            .findFirst()
            .orElse(null);
    }

    private static String stripOperatorSymbols(final String key) {
        return key.replaceAll("[<>*]", "");
    }

    private void trySetOrderBy(
        final QueryFilter queryFilter,
        final CriteriaBuilder criteriaBuilder,
        final Root<?> root,
        final CriteriaQuery<?> criteriaQuery) {

        if (Objects.isNull(queryFilter.getSortBy())) {
            return;
        }
        var orders = new ArrayList<Order>();
        for (var sortByField : queryFilter.getSortBy()) {
            if (SortType.DESCENDING.equals(queryFilter.getSort())) {
                orders.add(criteriaBuilder.desc(root.get(sortByField)));
            } else {
                orders.add(criteriaBuilder.asc(root.get(sortByField)));
            }
        }
        criteriaQuery.orderBy(orders);
    }

    private PageRequest getPageRequest(
        final QueryFilter queryFilter, final int page, final int size) {

        var orders = new ArrayList<Sort.Order>();
        if (Objects.nonNull(queryFilter.getSortBy())) {
            var direction = SortType.DESCENDING.equals(queryFilter.getSort())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            for (var sortByField : queryFilter.getSortBy()) {
                orders.add(new Sort.Order(direction, sortByField));
            }
        }
        return PageRequest.of(page, Math.max(size, 1), Sort.by(orders));
    }
}
