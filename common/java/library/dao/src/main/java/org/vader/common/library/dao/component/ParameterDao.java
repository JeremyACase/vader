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
import java.util.Map;
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
import org.vader.common.library.dao.model.QueryFilterParameter;
import org.vader.common.library.dao.model.QueryOperatorType;
import org.vader.common.library.dao.service.builder.NestedPredicateBuilder;
import org.vader.common.library.dao.service.builder.NonNestedPredicateBuilder;
import org.vader.common.library.dao.service.logic.ClassDeriver;
import org.vader.common.library.dao.service.logic.EmbeddableDeriver;
import org.vader.common.library.dao.service.logic.EntityDeriver;

/**
 * Queries an entity type from the database given a raw request-parameter map, as produced by a
 * RESTful {@code GET} — {@code ?title=x&createdAt>=...&taskPlan.objective*=%party%}. A trailing
 * {@code <}, {@code >} or {@code *} on a key selects {@code LESS_THAN} / {@code GREATER_THAN} /
 * {@code LIKE}; otherwise {@code EQUAL}. A value of {@code null} / {@code !null} tests nullness.
 *
 * @param <T> the entity type
 */
@Component
public class ParameterDao<T> {

    private static final Logger logger = LoggerFactory.getLogger(ParameterDao.class);

    @Autowired
    private ClassDeriver classDeriver;

    @Autowired
    private EmbeddableDeriver embeddableDeriver;

    @Autowired
    private EntityDeriver entityDeriver;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NestedPredicateBuilder nestedPredicateBuilder;

    @Autowired
    private NonNestedPredicateBuilder nonNestedPredicateBuilder;

    /**
     * Returns a page of entities matching {@code parameters}.
     *
     * @param parameters the request-param map (each value array's first element is used)
     * @param page the zero-based page number
     * @param size the page size
     * @param sortDescending whether to sort descending
     * @param sortByFields the fields to sort by
     * @param clazz the entity class
     * @return the matching page
     * @throws NoSuchFieldException if a parameter references a field that does not exist
     */
    @Transactional
    public Page<T> getPage(
        final Map<String, String[]> parameters,
        final int page,
        final int size,
        final Boolean sortDescending,
        final List<String> sortByFields,
        final Class<T> clazz) throws NoSuchFieldException {

        var predicateClass = this.classDeriver.tryGetPredicateClass(
            clazz, new ArrayList<>(parameters.keySet()));

        var criteriaBuilder = this.entityManager.getCriteriaBuilder();
        var criteriaQuery = criteriaBuilder.createQuery(predicateClass);
        var root = criteriaQuery.from(predicateClass);

        this.trySetOrderBy(sortByFields, sortDescending, criteriaBuilder, root, criteriaQuery);
        var pageRequest = getPageRequest(sortByFields, sortDescending, page, size);

        var predicates = this.getPredicates(
            parameters, criteriaBuilder, criteriaQuery, root, predicateClass);
        criteriaQuery.where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));

        var listSize = Math.max(pageRequest.getPageSize(), 1);
        var records = this.entityManager.createQuery(criteriaQuery)
            .setFirstResult(pageRequest.getPageNumber() * pageRequest.getPageSize())
            .setHint("org.hibernate.cacheable", true)
            .setMaxResults(listSize)
            .getResultList();

        var count = this.getCount(parameters, clazz);
        @SuppressWarnings("unchecked")
        var paged = (GenericPageImplementation<T>) new GenericPageImplementation<>(
            records, pageRequest, count);
        return paged;
    }

    /**
     * Returns the number of entities matching {@code parameters}.
     *
     * @param parameters the request-param map
     * @param clazz the entity class
     * @return the match count
     * @throws NoSuchFieldException if a parameter references a field that does not exist
     */
    public Long getCount(
        final Map<String, String[]> parameters,
        final Class<?> clazz) throws NoSuchFieldException {

        var criteriaBuilder = this.entityManager.getCriteriaBuilder();
        var countQuery = criteriaBuilder.createQuery(Long.class);
        var countRoot = countQuery.from(clazz);

        var predicates = this.getPredicates(
            parameters, criteriaBuilder, countQuery, countRoot, clazz);

        countQuery
            .select(criteriaBuilder.count(countRoot))
            .where(criteriaBuilder.and(predicates.toArray(new Predicate[0])));

        return this.entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> getPredicates(
        final Map<String, String[]> parameters,
        final CriteriaBuilder criteriaBuilder,
        final CriteriaQuery<?> criteriaQuery,
        final Root<?> root,
        final Class<?> clazz) throws NoSuchFieldException {

        var predicates = new ArrayList<Predicate>();
        for (var keychain : parameters.keySet()) {
            var value = parameters.get(keychain)[0];
            var split = Arrays.asList(keychain.split("\\."));
            if (split.size() > 1) {
                predicates.add(this.getPredicateForNestedParameter(
                    criteriaBuilder, criteriaQuery, root, keychain, split, value, clazz));
            } else {
                predicates.add(this.getPredicateForParameter(
                    criteriaBuilder, split.get(0), value, root, clazz));
            }
        }
        return predicates;
    }

    private Predicate getPredicateForParameter(
        final CriteriaBuilder criteriaBuilder,
        final String fieldKey,
        final String value,
        final Root<?> root,
        final Class<?> clazz) throws NoSuchFieldException {

        var keyWord = stripOperatorSymbols(fieldKey);
        var match = FieldUtils.getAllFieldsList(clazz).stream()
            .filter(field -> field.getName().equalsIgnoreCase(keyWord))
            .findFirst();
        if (match.isEmpty()) {
            throw new NoSuchFieldException("Field '" + keyWord + "' (from '" + fieldKey
                + "') not found in " + clazz.getSimpleName());
        }
        return this.nonNestedPredicate(
            criteriaBuilder, match.get(), root, queryFilterParameter(fieldKey, value, keyWord));
    }

    private Predicate getPredicateForNestedParameter(
        final CriteriaBuilder criteriaBuilder,
        final CriteriaQuery<?> criteriaQuery,
        final Root<?> root,
        final String keychain,
        final List<String> split,
        final String queryValue,
        final Class<?> rootClass) throws NoSuchFieldException {

        var currentClass = rootClass;
        Class<?> previousClass = null;
        Field finalField = null;

        for (var segment : split) {
            var fieldName = stripOperatorSymbols(segment);
            var field = findFieldIgnoreCase(currentClass, fieldName);
            if (Objects.isNull(field)) {
                logger.debug("Field '{}' not on '{}', checking subclasses...",
                    fieldName, currentClass.getSimpleName());
                currentClass = this.classDeriver.tryGetPredicateClass(currentClass, fieldName);
                field = findFieldIgnoreCase(currentClass, fieldName);
                if (Objects.isNull(field)) {
                    throw new NoSuchFieldException("Field '" + fieldName + "' not found in "
                        + currentClass.getSimpleName() + " or its subclasses.");
                }
            }
            finalField = field;
            previousClass = currentClass;
            if (Collection.class.isAssignableFrom(field.getType())) {
                var collectionType = (ParameterizedType) field.getGenericType();
                currentClass = (Class<?>) collectionType.getActualTypeArguments()[0];
            } else {
                currentClass = field.getType();
            }
        }

        if (Objects.nonNull(previousClass)
            && this.embeddableDeriver.isEmbeddedClass(previousClass)) {
            Path<?> path = root;
            for (var segment : split) {
                path = path.get(segment);
            }
            return criteriaBuilder.equal(path, queryValue);
        }

        var subQuery = criteriaQuery.subquery(currentClass);
        Root<?> subRoot = this.entityDeriver.isEntityClass(currentClass)
            ? subQuery.from(currentClass)
            : subQuery.correlate(root);

        var join = subRoot.join(stripOperatorSymbols(split.get(0)));
        for (int i = 1; i < split.size() - 1; i++) {
            join = join.join(stripOperatorSymbols(split.get(i)));
        }
        var joinedPath = join.get(stripOperatorSymbols(split.get(split.size() - 1)));

        return this.nestedPredicate(
            criteriaBuilder, joinedPath, subQuery, keychain, finalField, queryValue);
    }

    private Predicate nonNestedPredicate(
        final CriteriaBuilder criteriaBuilder,
        final Field field,
        final Path<?> path,
        final QueryFilterParameter parameter) {

        if (parameter.getValue().equalsIgnoreCase("null")) {
            return criteriaBuilder.isNull(path.get(parameter.getKey()));
        }
        if (parameter.getValue().equalsIgnoreCase("!null")) {
            return criteriaBuilder.isNotNull(path.get(parameter.getKey()));
        }

        var type = field.getType();
        if (type.equals(OffsetDateTime.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperDate(
                criteriaBuilder, path, parameter);
        }
        if (type.equals(String.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperString(
                criteriaBuilder, path, parameter);
        }
        if (type.equals(Boolean.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperBoolean(
                criteriaBuilder, path, parameter);
        }
        if (type.equals(Integer.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperInteger(
                criteriaBuilder, path, parameter);
        }
        if (type.equals(Long.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperLong(
                criteriaBuilder, path, parameter);
        }
        if (type.equals(Float.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperFloat(
                criteriaBuilder, path, parameter);
        }
        if (type.equals(Double.class)) {
            return this.nonNestedPredicateBuilder.getPredicateHelperDouble(
                criteriaBuilder, path, parameter);
        }
        if (type.isEnum()) {
            return this.nonNestedPredicateBuilder.getPredicateHelperEnum(
                criteriaBuilder, path, field, parameter);
        }
        throw new IllegalArgumentException("Unsupported field type " + type.getSimpleName()
            + " for field " + parameter.getKey());
    }

    private Predicate nestedPredicate(
        final CriteriaBuilder criteriaBuilder,
        final Path<?> joinedPath,
        final Subquery<?> subQuery,
        final String keychain,
        final Field finalField,
        final String queryValue) {

        var type = finalField.getType();
        if (type.isEnum()) {
            return this.nestedPredicateBuilder.getNestedQueryPredicateEnum(
                criteriaBuilder, joinedPath, subQuery, finalField, queryValue);
        }
        Class<?> parseType = type.equals(OffsetDateTime.class) ? OffsetDateTime.class
            : type.equals(Integer.class) ? Integer.class
            : type.equals(Long.class) ? Long.class
            : type.equals(Double.class) ? Double.class
            : type.equals(Float.class) ? Float.class
            : type.equals(Boolean.class) ? Boolean.class
            : String.class;
        return this.nestedPredicateBuilder.getNestedQueryPredicate(
            keychain, criteriaBuilder, joinedPath, subQuery, queryValue, parseType);
    }

    private static QueryFilterParameter queryFilterParameter(
        final String fieldKey, final String value, final String keyWord) {

        var parameter = new QueryFilterParameter();
        parameter.setKey(keyWord);
        parameter.setValue(value);
        if (fieldKey.endsWith("<")) {
            parameter.setOperator(QueryOperatorType.LESS_THAN);
        } else if (fieldKey.endsWith(">")) {
            parameter.setOperator(QueryOperatorType.GREATER_THAN);
        } else if (fieldKey.endsWith("*")) {
            parameter.setOperator(QueryOperatorType.LIKE);
        } else {
            parameter.setOperator(QueryOperatorType.EQUAL);
        }
        return parameter;
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
        final List<String> sortByFields,
        final Boolean sortDescending,
        final CriteriaBuilder criteriaBuilder,
        final Root<?> root,
        final CriteriaQuery<?> criteriaQuery) {

        if (Objects.isNull(sortByFields)) {
            return;
        }
        var orders = new ArrayList<Order>();
        for (var sortByField : sortByFields) {
            orders.add(Boolean.TRUE.equals(sortDescending)
                ? criteriaBuilder.desc(root.get(sortByField))
                : criteriaBuilder.asc(root.get(sortByField)));
        }
        criteriaQuery.orderBy(orders);
    }

    private static PageRequest getPageRequest(
        final List<String> sortByFields,
        final Boolean sortDescending,
        final int page,
        final int size) {

        var orders = new ArrayList<Sort.Order>();
        if (Objects.nonNull(sortByFields)) {
            var direction = Boolean.TRUE.equals(sortDescending)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            for (var sortByField : sortByFields) {
                orders.add(new Sort.Order(direction, sortByField));
            }
        }
        return PageRequest.of(page, Math.max(size, 1), Sort.by(orders));
    }
}
