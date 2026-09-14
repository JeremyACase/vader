package org.vader.core.server.service.config.storage;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.vader.core.server.service.storage.ObjectStorageResourceHttpRequestHandler;
import org.vader.core.server.service.storage.ObjectStorageResourceResolver;
import org.vader.core.server.service.storage.ObjectStorageService;

/**
 * Wires {@code GET /vader/core-server/object-storage/{id}/content} to Spring's own
 * {@link ResourceHttpRequestHandler} instead of a hand-rolled controller, via a custom
 * {@code ObjectStorageResourceResolver} that resolves the id in the path to a stored object
 * through {@link ObjectStorageService} -- transparently to whichever storage strategy is active.
 * That handler already implements HTTP Range requests (including genuine
 * {@code multipart/byteranges} for multiple ranges), conditional GETs, and HEAD requests, so none
 * of that needs reimplementing here.
 *
 * <p>Wired directly as a {@link SimpleUrlHandlerMapping} rather than through
 * {@code WebMvcConfigurer.addResourceHandlers} -- that registry always builds a plain
 * {@link ResourceHttpRequestHandler}, with no seam to substitute the custom subclass needed here
 * to also set {@code Content-Disposition} and the object's recorded content type.</p>
 */
@Configuration
public class ObjectStorageResourceHandlerConfig {

    private static final String PATH_PATTERN = "/vader/core-server/object-storage/**";

    @Autowired
    private ObjectStorageService objectStorageService;

    /**
     * Builds the handler with no static locations -- every request is resolved dynamically by
     * {@code ObjectStorageResourceResolver}, which never falls through to a location.
     *
     * @return the configured resource handler
     */
    @Bean
    public ResourceHttpRequestHandler objectStorageResourceHandler() {
        var handler = new ObjectStorageResourceHttpRequestHandler();
        handler.setLocations(List.of());
        handler.setResourceResolvers(
            List.of(new ObjectStorageResourceResolver(this.objectStorageService)));
        // Our resources are backed by a byte array or a MinIO stream, neither of which has a
        // file to stat -- the default last-modified check calls Resource#getFile() and blows up
        // with a FileNotFoundException for both. None of our content has a meaningful mtime
        // anyway, so conditional-GET-by-Last-Modified is simply not a feature here.
        handler.setUseLastModified(false);
        return handler;
    }

    /**
     * Maps the object-storage download path to {@link #objectStorageResourceHandler()}.
     *
     * @param objectStorageResourceHandler the handler bean to map
     * @return the URL mapping
     */
    @Bean
    public SimpleUrlHandlerMapping objectStorageResourceHandlerMapping(
        final ResourceHttpRequestHandler objectStorageResourceHandler) {

        var mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(Ordered.LOWEST_PRECEDENCE - 1);
        mapping.setUrlMap(Map.of(PATH_PATTERN, objectStorageResourceHandler));
        return mapping;
    }
}
