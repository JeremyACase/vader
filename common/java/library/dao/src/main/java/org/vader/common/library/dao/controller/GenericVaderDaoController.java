package org.vader.common.library.dao.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.vader.common.library.dao.interfaces.InterfaceVaderDaoController;
import org.vader.common.library.dao.model.GenericPageImplementation;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.library.dao.service.PageValidator;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.model.vader.dto.AbstractModel;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Abstract, read-only RESTful controller over the DAO query engine. Subclasses annotate
 * themselves {@code @RestController @RequestMapping("/...")} and supply their entity DAO and
 * mapper via {@link InterfaceVaderDaoController}.
 *
 * <p>Exposes, relative to the subclass's base path:</p>
 * <ul>
 *   <li>{@code GET  /query/params} — filter by URL query params</li>
 *   <li>{@code GET  /query/{id}} — fetch one by id (200) or 204 if absent</li>
 *   <li>{@code POST /query} — filter by a structured {@link QueryFilter} body</li>
 *   <li>{@code POST /query/count} — count matches for a structured {@link QueryFilter} body</li>
 * </ul>
 *
 * @param <T> the entity type
 * @param <D> the DTO type
 */
public abstract class GenericVaderDaoController<
    T extends AbstractModelEntity,
    D extends AbstractModel>
    implements InterfaceVaderDaoController<T, D> {

    private static final Logger logger = LoggerFactory.getLogger(GenericVaderDaoController.class);
    private static final Pattern KEBAB_TO_CAMEL = Pattern.compile("-([a-z])");

    private final Class<T> entityClass;
    private final Class<D> dtoClass;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PageValidator pageValidator;

    /**
     * Caches the entity and DTO classes from the concrete subclass's type arguments.
     */
    @SuppressWarnings("unchecked")
    protected GenericVaderDaoController() {
        var superclass = (ParameterizedType) this.getClass().getGenericSuperclass();
        this.entityClass = (Class<T>) superclass.getActualTypeArguments()[0];
        this.dtoClass = (Class<D>) superclass.getActualTypeArguments()[1];
    }

    /**
     * Returns the entity class this controller queries.
     *
     * @return the entity class
     */
    public Class<T> getEntityClass() {
        return this.entityClass;
    }

    /**
     * Returns the DTO class this controller returns.
     *
     * @return the DTO class
     */
    public Class<D> getDtoClass() {
        return this.dtoClass;
    }

    /**
     * Queries by URL parameters, e.g. {@code ?title*=Arrange&createdAt>=2026-01-01T00:00:00Z}.
     *
     * @param page the zero-based page number
     * @param size the page size
     * @param sortDescending whether to sort descending (default true)
     * @param sortByFields the fields to sort by
     * @param request the servlet request whose remaining params become filter constraints
     * @return a page of matching DTOs
     * @throws NoSuchFieldException if a param names a field that does not exist
     */
    @GetMapping("/query/params")
    @Transactional
    public GenericPageImplementation<D> queryWithParams(
        @RequestParam("page") final Integer page,
        @RequestParam("size") final Integer size,
        @RequestParam(value = "sort-descending", required = false, defaultValue = "true")
        final Boolean sortDescending,
        @RequestParam(value = "sort-by-fields", required = false, defaultValue = "")
        final List<String> sortByFields,
        final HttpServletRequest request) throws NoSuchFieldException {

        this.pageValidator.validatePageAndSize(page, size);
        var params = this.paramMapFrom(request);
        var records = this.getDataAccessObject().getPage(
            params, page, size, sortDescending, sortByFields, this.entityClass);
        return this.toDtoPage(records);
    }

    /**
     * Fetches a single record by id.
     *
     * @param id the record id
     * @return the DTO (200) or an empty 204 if not found
     * @throws NoSuchFieldException never in practice (the {@code id} field always exists)
     */
    @GetMapping("/query/{id}")
    @Transactional
    public ResponseEntity<D> queryById(@PathVariable("id") final String id)
        throws NoSuchFieldException {

        var params = new HashMap<String, String[]>();
        params.put("id", new String[] {id});
        var records = this.getDataAccessObject().getPage(
            params, 0, 1, true, new ArrayList<>(), this.entityClass);

        if (records.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.ok(this.toDtoPage(records).getContent().get(0));
    }

    /**
     * Queries by a structured filter.
     *
     * @param queryFilter the filter (constraints, sort, page, page size)
     * @return a page of matching DTOs
     * @throws NoSuchFieldException if the filter names a field that does not exist
     */
    @PostMapping("/query")
    @Transactional
    public GenericPageImplementation<D> query(@RequestBody final QueryFilter queryFilter)
        throws NoSuchFieldException {

        this.pageValidator.validatePageAndSize(queryFilter.getPage(), queryFilter.getPageSize());
        var records = this.getDataAccessObject().getPage(
            queryFilter, queryFilter.getPage(), queryFilter.getPageSize(), this.entityClass);
        return this.toDtoPage(records);
    }

    /**
     * Counts records matching a structured filter.
     *
     * @param queryFilter the filter
     * @return {@code { "count": <n> }}
     * @throws NoSuchFieldException if the filter names a field that does not exist
     */
    @PostMapping("/query/count")
    @Transactional
    public Map<String, Long> count(@RequestBody final QueryFilter queryFilter)
        throws NoSuchFieldException {
        var count = this.getDataAccessObject().getCount(queryFilter, this.entityClass);
        return Map.of("count", count);
    }

    /**
     * Maps a page of entities to a page of DTOs, preserving the pagination metadata.
     *
     * @param records the entity page
     * @return the DTO page
     */
    protected GenericPageImplementation<D> toDtoPage(final Page<T> records) {
        var mapper = this.getDataTransferObjectMapper();
        var content = mapper.map(records.getContent());
        return new GenericPageImplementation<>(
            content,
            records.getNumber(),
            records.getSize(),
            records.getTotalElements(),
            this.objectMapper.valueToTree(records.getPageable()),
            records.isLast(),
            records.getTotalPages(),
            this.objectMapper.valueToTree(records.getSort()),
            records.isFirst(),
            records.getNumberOfElements(),
            records.getContent().isEmpty());
    }

    private Map<String, String[]> paramMapFrom(final HttpServletRequest request) {
        var params = new HashMap<String, String[]>();
        request.getParameterMap().forEach((key, value) -> {
            var camel = KEBAB_TO_CAMEL.matcher(key)
                .replaceAll(match -> match.group(1).toUpperCase());
            params.put(camel, value);
        });
        params.keySet().removeAll(List.of(
            "page", "size", "sortDescending", "sortByFields", "sortBy"));
        logger.debug("Parsed {} filter param(s) for {}", params.size(),
            this.entityClass.getSimpleName());
        return params;
    }

    /**
     * Returns the logger shared by this base controller.
     *
     * @return the logger
     */
    protected static Logger logger() {
        return logger;
    }
}
