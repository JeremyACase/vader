package org.vader.core.server.service.tools.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.exceptions.ObjectNotFoundException;
import org.vader.core.server.models.EncodedObjectContent;
import org.vader.core.server.service.storage.ObjectContent;
import org.vader.core.server.service.storage.ObjectDescriptor;
import org.vader.core.server.service.storage.ObjectStorageService;

class ObjectStorageToolsTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private ObjectStorageService objectStorageService;
    private ObjectStorageTools tools;

    @BeforeEach
    void setUp() {
        this.objectStorageService = mock(ObjectStorageService.class);
        this.tools = new ObjectStorageTools();
        ReflectionTestUtils.setField(this.tools, "objectStorageService", this.objectStorageService);
        ReflectionTestUtils.setField(this.tools, "maxInlineBytes", 1024L);
    }

    @Test
    void getObjectContent_withinTheInlineLimit_returnsBase64EncodedContent() {
        when(this.objectStorageService.describe(ID))
            .thenReturn(new ObjectDescriptor(ID, "greeting.txt", "text/plain", 11));
        when(this.objectStorageService.retrieve(ID)).thenReturn(new ObjectContent(
            new ByteArrayResource("hello world".getBytes()), "greeting.txt", "text/plain", 11));

        var result = this.tools.getObjectContent(ID);

        assertThat(result).isInstanceOf(EncodedObjectContent.class);
        var content = (EncodedObjectContent) result;
        assertThat(content.filename()).isEqualTo("greeting.txt");
        assertThat(content.contentType()).isEqualTo("text/plain");
        assertThat(content.size()).isEqualTo(11);
        assertThat(Base64.getDecoder().decode(content.base64Content()))
            .isEqualTo("hello world".getBytes());
    }

    @Test
    void getObjectContent_overTheInlineLimit_returnsAnErrorWithTheDownloadUrl() {
        when(this.objectStorageService.describe(ID))
            .thenReturn(new ObjectDescriptor(ID, "huge.txt", "text/plain", 5000));

        var result = this.tools.getObjectContent(ID);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        var map = (Map<String, String>) result;
        assertThat(map.get("error"))
            .contains("/vader/core-server/object-storage/" + ID + "/content");
    }

    @Test
    void getObjectContent_withUnknownId_returnsAnErrorInsteadOfThrowing() {
        when(this.objectStorageService.describe(ID)).thenThrow(new ObjectNotFoundException(ID));

        var result = this.tools.getObjectContent(ID);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        var map = (Map<String, String>) result;
        assertThat(map.get("error")).contains(ID);
    }

    @Test
    void getObjectContent_withBinaryContentType_refusesRegardlessOfSize() {
        when(this.objectStorageService.describe(ID)).thenReturn(new ObjectDescriptor(
            ID, "EP_Tactics.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 100));

        var result = this.tools.getObjectContent(ID);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        var map = (Map<String, String>) result;
        assertThat(map.get("error"))
            .contains("EP_Tactics.xlsx")
            .contains("run_python_code")
            .doesNotContain("stage_object");
    }

    @Test
    void getObjectContent_withJsonContentType_stillInlinesIt() {
        when(this.objectStorageService.describe(ID))
            .thenReturn(new ObjectDescriptor(ID, "data.json", "application/json", 2));
        when(this.objectStorageService.retrieve(ID)).thenReturn(new ObjectContent(
            new ByteArrayResource("{}".getBytes()), "data.json", "application/json", 2));

        var result = this.tools.getObjectContent(ID);

        assertThat(result).isInstanceOf(EncodedObjectContent.class);
    }
}
