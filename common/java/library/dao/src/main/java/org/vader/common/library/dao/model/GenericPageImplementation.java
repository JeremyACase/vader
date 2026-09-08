package org.vader.common.library.dao.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * A {@link PageImpl} that serializes to (and deserializes from) a stable JSON shape, so query
 * results can be returned over HTTP and read back by a client.
 *
 * <p>The server side builds these with {@link #GenericPageImplementation(List, Pageable, long)};
 * the {@link JsonCreator} constructor exists only so callers can deserialize the response.</p>
 *
 * @param <T> the element type
 */
public class GenericPageImplementation<T> extends PageImpl<T> {

    private static final long serialVersionUID = 1L;

    /**
     * Deserialization constructor.
     *
     * @param content the page content
     * @param number the zero-based page number
     * @param size the page size
     * @param totalElements the total number of matching elements
     * @param pageable the pageable metadata (ignored on read)
     * @param last whether this is the last page (ignored on read)
     * @param totalPages the total page count (ignored on read)
     * @param sort the sort metadata (ignored on read)
     * @param first whether this is the first page (ignored on read)
     * @param numberOfElements the number of elements on this page (ignored on read)
     * @param empty whether the page is empty (ignored on read)
     */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public GenericPageImplementation(
        @JsonProperty("content") final List<T> content,
        @JsonProperty("number") final int number,
        @JsonProperty("size") final int size,
        @JsonProperty("totalElements") final Long totalElements,
        @JsonProperty("pageable") final JsonNode pageable,
        @JsonProperty("last") final boolean last,
        @JsonProperty("totalPages") final int totalPages,
        @JsonProperty("sort") final JsonNode sort,
        @JsonProperty("first") final boolean first,
        @JsonProperty("numberOfElements") final int numberOfElements,
        @JsonProperty("empty") final boolean empty) {

        super(content, PageRequest.of(number, Math.max(size, 1)), totalElements);
    }

    /**
     * Server-side constructor.
     *
     * @param content the page content
     * @param pageable the page request that produced this content
     * @param totalElements the total number of matching elements
     */
    public GenericPageImplementation(
        final List<T> content, final Pageable pageable, final long totalElements) {
        super(content, pageable, totalElements);
    }

    /**
     * Constructs an unpaged page over {@code content}.
     *
     * @param content the page content
     */
    public GenericPageImplementation(final List<T> content) {
        super(content);
    }

    /**
     * Constructs an empty page.
     */
    public GenericPageImplementation() {
        super(new ArrayList<>());
    }
}
