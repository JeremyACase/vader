package org.vader.core.server.service.strategies.storage;

import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;

/**
 * An {@link InputStreamResource} that reports a length known ahead of time instead of the
 * default {@code -1}.
 *
 * <p>{@code ResourceRegionHttpMessageConverter} needs {@link #contentLength()} to build a
 * correct {@code Content-Range} total when serving a byte-range request. A plain
 * {@link InputStreamResource} cannot answer that without consuming the stream, but MinIO already
 * tells us the object's size up front (it is persisted on {@code ObjectMetadataEntity}), so there
 * is no need to guess or re-fetch it.</p>
 */
class SizedInputStreamResource extends InputStreamResource {

    private final long length;

    /**
     * Wraps a stream with a length that is already known.
     *
     * @param inputStream the object's content stream
     * @param length the object's total size in bytes
     */
    SizedInputStreamResource(final InputStream inputStream, final long length) {
        super(inputStream);
        this.length = length;
    }

    @Override
    public long contentLength() {
        return this.length;
    }
}
