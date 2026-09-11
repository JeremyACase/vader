package org.vader.core.server.models;

import java.util.List;
import org.vader.common.model.vader.dto.AbstractModel;

/**
 * A page of query results, trimmed to what an LLM needs (no Spring pageable/sort envelope).
 *
 * @param content the matching DTOs on this page
 * @param totalElements the total number of matches across all pages
 * @param page the zero-based page number returned
 * @param pageSize the page size
 * @param totalPages the total number of pages
 */
public record QueryResult(
    List<? extends AbstractModel> content,
    long totalElements,
    int page,
    int pageSize,
    int totalPages) {
}
