package org.vader.common.library.dao.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * A structured, {@code AND}-combined set of constraints for querying an entity type.
 *
 * <p>Every {@link QueryFilterParameter} in {@code parameters} must match. {@code sortBy} names
 * fields to order by, in {@code sort} direction. {@code page} / {@code pageSize} paginate the
 * result.</p>
 */
public class QueryFilter {

    private SortType sort;

    @Valid
    private List<String> sortBy;

    @Valid
    private List<QueryFilterParameter> parameters = new ArrayList<>();

    @NotNull
    private Integer page = 0;

    @NotNull
    private Integer pageSize = 10;

    /**
     * Returns the sort direction, or {@code null} for the default (ascending).
     *
     * @return the sort direction
     */
    public SortType getSort() {
        return this.sort;
    }

    public void setSort(final SortType sort) {
        this.sort = sort;
    }

    /**
     * Returns the fields to order the result by, or {@code null} for no explicit ordering.
     *
     * @return the sort-by fields
     */
    public List<String> getSortBy() {
        return this.sortBy;
    }

    public void setSortBy(final List<String> sortBy) {
        this.sortBy = sortBy;
    }

    /**
     * Returns the constraints, all of which must match.
     *
     * @return the parameters
     */
    public List<QueryFilterParameter> getParameters() {
        return this.parameters;
    }

    public void setParameters(final List<QueryFilterParameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the zero-based page number.
     *
     * @return the page
     */
    public Integer getPage() {
        return this.page;
    }

    public void setPage(final Integer page) {
        this.page = page;
    }

    /**
     * Returns the page size.
     *
     * @return the page size
     */
    public Integer getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(final Integer pageSize) {
        this.pageSize = pageSize;
    }
}
