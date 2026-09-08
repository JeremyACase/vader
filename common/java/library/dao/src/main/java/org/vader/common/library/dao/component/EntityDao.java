package org.vader.common.library.dao.component;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.vader.common.library.dao.model.QueryFilter;

/**
 * The single entry point for dynamic queries against an entity type: a thin facade over
 * {@link FilterDao} (structured {@link QueryFilter}) and {@link ParameterDao} (request-param
 * map). Stateless — the entity {@code Class} is passed to every call.
 *
 * @param <T> the entity type
 */
@Component
public class EntityDao<T> {

    @Autowired
    private FilterDao<T> filterDao;

    @Autowired
    private ParameterDao<T> parameterDao;

    /**
     * Counts entities matching a request-param map.
     *
     * @param parameters the request-param map
     * @param clazz the entity class
     * @return the match count
     * @throws NoSuchFieldException if a parameter references an unknown field
     */
    public Long getCount(final Map<String, String[]> parameters, final Class<T> clazz)
        throws NoSuchFieldException {
        return this.parameterDao.getCount(parameters, clazz);
    }

    /**
     * Counts entities matching a structured filter.
     *
     * @param queryFilter the filter
     * @param clazz the entity class
     * @return the match count
     * @throws NoSuchFieldException if the filter references an unknown field
     */
    public Long getCount(final QueryFilter queryFilter, final Class<T> clazz)
        throws NoSuchFieldException {
        return this.filterDao.getCount(queryFilter, clazz);
    }

    /**
     * Returns a page of entities matching a structured filter.
     *
     * @param queryFilter the filter
     * @param page the zero-based page number
     * @param size the page size
     * @param clazz the entity class
     * @return the matching page
     * @throws NoSuchFieldException if the filter references an unknown field
     */
    @Transactional
    public Page<T> getPage(
        final QueryFilter queryFilter,
        final Integer page,
        final Integer size,
        final Class<T> clazz) throws NoSuchFieldException {
        return this.filterDao.getPage(queryFilter, page, size, clazz);
    }

    /**
     * Returns a page of entities matching a request-param map.
     *
     * @param parameters the request-param map
     * @param page the zero-based page number
     * @param size the page size
     * @param sortDescending whether to sort descending
     * @param sortByFields the fields to sort by
     * @param clazz the entity class
     * @return the matching page
     * @throws NoSuchFieldException if a parameter references an unknown field
     */
    @Transactional
    public Page<T> getPage(
        final Map<String, String[]> parameters,
        final Integer page,
        final Integer size,
        final Boolean sortDescending,
        final List<String> sortByFields,
        final Class<T> clazz) throws NoSuchFieldException {
        return this.parameterDao.getPage(
            parameters, page, size, sortDescending, sortByFields, clazz);
    }
}
