package org.vader.core.server.service.config.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.vader.core.server.service.storage.ObjectContent;
import org.vader.core.server.service.storage.ObjectStorageService;

/**
 * Exercises the actual {@code @Bean} the config produces, not a hand-wired stand-in, because the
 * one real bug found in this handler (a {@code FileNotFoundException} from the base class's
 * default last-modified check against a non-file-backed resource) was in the wiring here --
 * specifically, forgetting {@code setUseLastModified(false)} -- and no amount of testing the
 * handler class in isolation with that flag pre-set would have caught it.
 */
class ObjectStorageResourceHandlerConfigTest {

    @Test
    void objectStorageResourceHandler_downloadsByteArrayResourceWithoutThrowing()
        throws Exception {

        var id = "11111111-1111-1111-1111-111111111111";
        var objectStorageService = mock(ObjectStorageService.class);
        when(objectStorageService.retrieve(id)).thenReturn(new ObjectContent(
            new ByteArrayResource("hello world".getBytes()), "greeting.txt", "text/plain", 11));

        var config = new ObjectStorageResourceHandlerConfig();
        ReflectionTestUtils.setField(config, "objectStorageService", objectStorageService);
        var handler = config.objectStorageResourceHandler();
        handler.afterPropertiesSet();

        var request = new MockHttpServletRequest(
            "GET", "/vader/core-server/object-storage/" + id + "/content");
        request.setAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, id + "/content");
        var response = new MockHttpServletResponse();

        handler.handleRequest(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("hello world");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=\"greeting.txt\"");
    }
}
