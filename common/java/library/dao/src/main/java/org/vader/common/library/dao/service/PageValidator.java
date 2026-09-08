package org.vader.common.library.dao.service;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Validates pagination parameters against the configured maximum page size.
 */
@Service
public class PageValidator {

    @Value("${vader.dao.max-page-size:100}")
    private Integer maxPageSize;

    /**
     * Ensures {@code page} and {@code size} are non-null and {@code size} does not exceed the
     * configured maximum.
     *
     * @param page the zero-based page number
     * @param size the page size
     * @throws IllegalArgumentException if either is null or {@code size} exceeds the maximum
     */
    public void validatePageAndSize(final Integer page, final Integer size) {
        if (Objects.isNull(page)) {
            throw new IllegalArgumentException("page cannot be null");
        }
        if (Objects.isNull(size)) {
            throw new IllegalArgumentException("size cannot be null");
        }
        if (size > this.maxPageSize) {
            throw new IllegalArgumentException(
                "requested page size " + size + " exceeds the maximum of " + this.maxPageSize);
        }
    }

    /**
     * Returns the configured maximum page size.
     *
     * @return the maximum page size
     */
    public Integer getMaxPageSize() {
        return this.maxPageSize;
    }
}
