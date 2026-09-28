package org.vader.core.server.service.storage;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.vader.core.server.exceptions.ObjectNotFoundException;

/**
 * Resolves the {@code {id}/content} portion of the object-storage download path to a stored
 * object's content, through {@link ObjectStorageService} -- transparently to whichever
 * {@code InterfaceFileStorageStrategy} is active.
 *
 * <p>Never falls through to a static location, so the owning {@code ResourceHttpRequestHandler}
 * is expected to have an empty locations list -- that's not a misconfiguration, it's the whole
 * point of resolving every request dynamically instead.</p>
 */
public class ObjectStorageResourceResolver implements ResourceResolver {

    private static final String CONTENT_SEGMENT = "content";

    private final ObjectStorageService objectStorageService;

    /**
     * Creates the resolver.
     *
     * @param objectStorageService looks up a stored object's content by id
     */
    public ObjectStorageResourceResolver(final ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @Override
    public Resource resolveResource(
        final HttpServletRequest request, final String requestPath,
        final List<? extends Resource> locations, final ResourceResolverChain chain) {

        var id = idOrNull(requestPath);
        return id != null ? tryRetrieve(id) : null;
    }

    @Override
    public String resolveUrlPath(
        final String resourcePath, final List<? extends Resource> locations,
        final ResourceResolverChain chain) {
        return null;
    }

    private String idOrNull(final String requestPath) {
        var segments = requestPath.split("/", 2);
        return segments.length == 2 && CONTENT_SEGMENT.equals(segments[1]) ? segments[0] : null;
    }

    private Resource tryRetrieve(final String id) {
        Resource resource;
        try {
            var content = this.objectStorageService.retrieve(id);
            resource = new DescribedResource(
                content.resource(), content.filename(), content.contentType());
        } catch (ObjectNotFoundException e) {
            resource = null;
        }
        return resource;
    }
}
