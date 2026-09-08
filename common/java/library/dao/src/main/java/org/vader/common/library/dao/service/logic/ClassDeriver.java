package org.vader.common.library.dao.service.logic;

import static org.reflections.scanners.Scanners.SubTypes;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vader.common.library.dao.model.QueryFilter;

/**
 * Resolves the concrete class a predicate should be built against.
 *
 * <p>For a JOINED inheritance hierarchy, a filter may name a field that lives on a subclass
 * rather than the queried base class. This service scans {@code org.vader} for the subclass that
 * declares the field and returns it; if the field is on the base class (or no subclass has it in
 * a resolvable way) it returns the base class or throws.</p>
 */
@Service
public class ClassDeriver {

    private static final Logger logger = LoggerFactory.getLogger(ClassDeriver.class);

    private final Reflections reflections = new Reflections("org.vader");

    /**
     * Resolves the predicate class for a structured filter.
     *
     * @param clazz the base (queried) class
     * @param queryFilter the client filter
     * @return the base class, or the subclass that declares a referenced field
     * @throws NoSuchFieldException if a referenced field cannot be found on the class or any
     *     subclass
     */
    public Class<?> tryGetPredicateClass(final Class<?> clazz, final QueryFilter queryFilter)
        throws NoSuchFieldException {

        var fieldNames = new ArrayList<String>();
        for (var parameter : queryFilter.getParameters()) {
            fieldNames.add(firstSegment(parameter.getKey()));
        }
        return this.resolve(clazz, fieldNames);
    }

    /**
     * Resolves the predicate class for a set of keychains.
     *
     * @param clazz the base (queried) class
     * @param keychains the keychains referenced by the query
     * @return the base class, or the subclass that declares a referenced field
     * @throws NoSuchFieldException if a referenced field cannot be found
     */
    public Class<?> tryGetPredicateClass(final Class<?> clazz, final List<String> keychains)
        throws NoSuchFieldException {

        var fieldNames = new ArrayList<String>();
        for (var keychain : keychains) {
            fieldNames.add(firstSegment(keychain));
        }
        return this.resolve(clazz, fieldNames);
    }

    /**
     * Resolves the predicate class for a single keychain.
     *
     * @param clazz the base (queried) class
     * @param keychain the keychain referenced by the query
     * @return the base class, or the subclass that declares the referenced field
     * @throws NoSuchFieldException if the referenced field cannot be found
     */
    public Class<?> tryGetPredicateClass(final Class<?> clazz, final String keychain)
        throws NoSuchFieldException {
        return this.resolve(clazz, List.of(firstSegment(keychain)));
    }

    private Class<?> resolve(final Class<?> clazz, final List<String> fieldNames)
        throws NoSuchFieldException {

        Class<?> predicateClass = clazz;

        for (var fieldName : fieldNames) {
            var onBaseClass = FieldUtils.getAllFieldsList(clazz).stream()
                .anyMatch(field -> field.getName().equalsIgnoreCase(fieldName));
            if (onBaseClass) {
                continue;
            }

            logger.debug("Field '{}' not on '{}'; checking subclasses...",
                fieldName, clazz.getSimpleName());

            var candidates = new ArrayList<Class<?>>();
            for (var subclass : this.reflections.get(SubTypes.of(clazz).asClass())) {
                var hasField = FieldUtils.getAllFieldsList(subclass).stream()
                    .anyMatch(field -> field.getName().equalsIgnoreCase(fieldName));
                if (hasField) {
                    candidates.add(subclass);
                }
            }

            if (candidates.size() > 1) {
                throw new IllegalArgumentException("Field '" + fieldName + "' exists on multiple "
                    + "subclasses of " + clazz.getSimpleName() + ": " + candidates
                    + "; cannot disambiguate.");
            }
            if (candidates.isEmpty()) {
                throw new NoSuchFieldException("Field '" + fieldName + "' not found on "
                    + clazz.getSimpleName() + " or its subclasses.");
            }
            predicateClass = candidates.get(0);
        }

        return predicateClass;
    }

    private static String firstSegment(final String keychain) {
        return keychain.split("\\.")[0].replaceAll("[<>*]", "");
    }
}
