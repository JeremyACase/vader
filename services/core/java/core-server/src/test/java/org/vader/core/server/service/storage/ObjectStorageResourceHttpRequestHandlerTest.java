package org.vader.core.server.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class ObjectStorageResourceHttpRequestHandlerTest {

    private ObjectStorageResourceHttpRequestHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        this.handler = new ObjectStorageResourceHttpRequestHandler();
        this.request = new MockHttpServletRequest();
    }

    @Test
    void getMediaType_withRecordedContentType_honorsIt() {
        var resource = new DescribedResource(
            new ByteArrayResource("data".getBytes()), "diagram.txt", "image/png");

        var mediaType = this.handler.getMediaType(this.request, resource);

        assertThat(mediaType).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void getMediaType_withNoRecordedContentType_fallsBackToTheDefault() {
        var resource = new DescribedResource(
            new ByteArrayResource("data".getBytes()), "diagram.png", null);

        var mediaType = this.handler.getMediaType(this.request, resource);

        assertThat(mediaType).isEqualTo(MediaType.IMAGE_PNG);
    }

    @Test
    void setHeaders_addsContentDispositionWithTheFilename() throws Exception {
        var resource = new DescribedResource(
            new ByteArrayResource("hello world".getBytes()), "greeting.txt", "text/plain");
        var response = new MockHttpServletResponse();

        this.handler.setHeaders(response, resource, MediaType.TEXT_PLAIN);

        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=\"greeting.txt\"");
    }

    /**
     * Exercises the real {@code handleRequest} path end to end (not just the overridden hooks in
     * isolation), which is what actually catches wiring bugs like the base class's default
     * last-modified check calling {@code Resource#getFile()} -- something a
     * {@link ByteArrayResource} or a MinIO-backed stream can never support.
     */
    @Test
    void handleRequest_fullRoundTrip_downloadsWithoutThrowing() throws Exception {
        var id = "11111111-1111-1111-1111-111111111111";
        var objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.retrieve(id)).thenReturn(new ObjectContent(
            new ByteArrayResource("hello world".getBytes()), "greeting.txt", "text/plain", 11));

        var fullHandler = new ObjectStorageResourceHttpRequestHandler();
        fullHandler.setLocations(List.of());
        fullHandler.setResourceResolvers(
            List.of(new ObjectStorageResourceResolver(objectStorageService)));
        fullHandler.setUseLastModified(false);
        fullHandler.afterPropertiesSet();

        var fullRequest = new MockHttpServletRequest(
            "GET", "/vader/core-server/object-storage/" + id + "/content");
        fullRequest.setAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, id + "/content");
        var response = new MockHttpServletResponse();

        fullHandler.handleRequest(fullRequest, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("hello world");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=\"greeting.txt\"");
    }
}
