package org.vader.core.server.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.vader.core.server.exceptions.ObjectNotFoundException;

class ObjectStorageResourceResolverTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private ObjectStorageService objectStorageService;
    private ObjectStorageResourceResolver resolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        this.objectStorageService = mock(ObjectStorageService.class);
        this.resolver = new ObjectStorageResourceResolver(this.objectStorageService);
        this.request = new MockHttpServletRequest();
    }

    @Test
    void resolveResource_withValidPath_returnsTheObjectsContent() throws Exception {
        when(this.objectStorageService.retrieve(ID)).thenReturn(new ObjectContent(
            new ByteArrayResource("hello world".getBytes()), "greeting.txt", "text/plain", 11));

        var resource = this.resolver.resolveResource(
            this.request, ID + "/content", List.of(), null);

        assertThat(resource).isNotNull();
        assertThat(resource.getFilename()).isEqualTo("greeting.txt");
        assertThat(resource.contentLength()).isEqualTo(11);
        assertThat(resource.getInputStream().readAllBytes()).isEqualTo("hello world".getBytes());
    }

    @Test
    void resolveResource_withAnUnknownId_returnsNull() {
        when(this.objectStorageService.retrieve(ID)).thenThrow(new ObjectNotFoundException(ID));

        var resource = this.resolver.resolveResource(
            this.request, ID + "/content", List.of(), null);

        assertThat(resource).isNull();
    }

    @Test
    void resolveResource_withoutTheContentSegment_returnsNullWithoutCallingTheService() {
        var resource = this.resolver.resolveResource(this.request, ID, List.of(), null);

        assertThat(resource).isNull();
    }

    @Test
    void resolveResource_withExtraPathSegments_returnsNull() {
        var resource = this.resolver.resolveResource(
            this.request, ID + "/content/extra", List.of(), null);

        assertThat(resource).isNull();
    }

    @Test
    void resolveUrlPath_isNotSupported() {
        assertThat(this.resolver.resolveUrlPath(ID + "/content", List.of(), null)).isNull();
    }
}
