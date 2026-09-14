package org.vader.core.server.service.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;

/**
 * Decorates a stored object's content {@link Resource} with the filename and content type
 * recorded at upload time.
 *
 * <p>{@link ObjectStorageResourceHttpRequestHandler}'s per-resource callbacks receive only the
 * {@link Resource} itself, not the {@link ObjectContent} it came from, so those two fields have
 * to travel on the resource for the handler to read them back off it.</p>
 */
class DescribedResource extends AbstractResource {

    private final Resource delegate;
    private final String filename;
    private final String contentType;

    /**
     * Wraps a resource with the descriptive fields recorded for it.
     *
     * @param delegate the object's actual content
     * @param filename the original filename
     * @param contentType the stored MIME type, or {@code null} if never recorded
     */
    DescribedResource(final Resource delegate, final String filename, final String contentType) {
        this.delegate = delegate;
        this.filename = filename;
        this.contentType = contentType;
    }

    String getContentType() {
        return this.contentType;
    }

    @Override
    public String getFilename() {
        return this.filename;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.delegate.getInputStream();
    }

    @Override
    public long contentLength() throws IOException {
        return this.delegate.contentLength();
    }

    @Override
    public String getDescription() {
        return this.delegate.getDescription();
    }
}
