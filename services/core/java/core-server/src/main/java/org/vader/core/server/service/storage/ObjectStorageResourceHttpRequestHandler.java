package org.vader.core.server.service.storage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * Adds the two things the base {@link ResourceHttpRequestHandler} doesn't do on its own: honoring
 * the object's originally recorded content type (rather than guessing one from the filename
 * extension) and sending {@code Content-Disposition: attachment} so browsers download rather than
 * render the response inline.
 *
 * <p>Everything else -- Range requests (including genuine {@code multipart/byteranges} for
 * multiple ranges), conditional GETs (ETag/Last-Modified), and HEAD requests -- comes from the
 * base class unchanged.</p>
 */
public class ObjectStorageResourceHttpRequestHandler extends ResourceHttpRequestHandler {

    @Override
    protected MediaType getMediaType(final HttpServletRequest request, final Resource resource) {
        var mediaType = super.getMediaType(request, resource);
        if (resource instanceof DescribedResource described && described.getContentType() != null) {
            mediaType = MediaType.parseMediaType(described.getContentType());
        }
        return mediaType;
    }

    @Override
    protected void setHeaders(
        final HttpServletResponse response, final Resource resource, final MediaType mediaType)
        throws IOException {

        super.setHeaders(response, resource, mediaType);
        if (resource.getFilename() != null) {
            response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + resource.getFilename() + "\"");
        }
    }
}
