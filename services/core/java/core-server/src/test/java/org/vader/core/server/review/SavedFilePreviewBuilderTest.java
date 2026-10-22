package org.vader.core.server.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.vader.core.server.storage.ObjectContent;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.storage.ObjectStorageService;

@ExtendWith(MockitoExtension.class)
class SavedFilePreviewBuilderTest {

    private static final String ATTEMPT_ID = UUID.randomUUID().toString();

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private SavedFilePreviewBuilder builder;

    private ObjectDescriptor stored(
            final String filename, final String contentType, final byte[] content) {
        var id = UUID.randomUUID().toString();
        when(this.objectStorageService.retrieve(id)).thenReturn(new ObjectContent(
            new ByteArrayResource(content), filename, contentType, content.length));
        return new ObjectDescriptor(id, filename, contentType, content.length);
    }

    private void saved(final ObjectDescriptor... files) {
        when(this.objectStorageService.describeTaskAttemptOutputs(ATTEMPT_ID))
            .thenReturn(List.of(files));
    }

    private static byte[] utf8(final String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static String lines(final int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(number -> "line " + number)
            .collect(Collectors.joining("\n"));
    }

    @Test
    void build_showsShortTextFilesWhole() {
        saved(stored("monthly_revenue.csv", "text/csv", utf8("month,revenue\r\n2024-01,1\r\n")));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.preview()).isEqualTo("month,revenue\n2024-01,1");
        assertThat(preview.truncated()).isFalse();
        assertThat(preview.binary()).isFalse();
    }

    @Test
    void build_showsOnlyTheOpeningLinesOfLongerFiles() {
        saved(stored("report.md", "text/markdown", utf8(lines(50))));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.preview()).isEqualTo(lines(SavedFilePreviewBuilder.MAX_LINES));
        assertThat(preview.truncated()).isTrue();
    }

    @Test
    void build_readsNoMoreThanTheHeadOfLargeFiles() {
        var oneLongLine = "x".repeat(SavedFilePreviewBuilder.MAX_BYTES_PER_FILE * 3);
        saved(stored("data.json", "application/json", utf8(oneLongLine)));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.preview()).hasSize(SavedFilePreviewBuilder.MAX_BYTES_PER_FILE);
        assertThat(preview.truncated()).isTrue();
    }

    @Test
    void build_showsTextWhateverTypeItWasRecordedUnder() {
        saved(stored("results", "application/octet-stream", utf8("accuracy: 0.93")));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.preview()).isEqualTo("accuracy: 0.93");
        assertThat(preview.binary()).isFalse();
    }

    @Test
    void build_neverShowsBinaryContent() {
        var zipHeader = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        saved(stored("report.xlsx", "application/octet-stream", zipHeader));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.preview()).isNull();
        assertThat(preview.binary()).isTrue();
    }

    @Test
    void build_doesNotMistakeCharactersCutByTheByteLimitForBinary() {
        var content = "a" + "é".repeat(SavedFilePreviewBuilder.MAX_BYTES_PER_FILE);
        saved(stored("notes.dat", "application/octet-stream", utf8(content)));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.binary()).isFalse();
        assertThat(preview.preview()).startsWith("aé").doesNotContain("�");
    }

    @Test
    void build_listsFilesPastThePreviewBudgetWithoutReadingThem() {
        var fileCount = SavedFilePreviewBuilder.MAX_TOTAL_CHARACTERS
            / SavedFilePreviewBuilder.MAX_BYTES_PER_FILE;
        var full = utf8("y".repeat(SavedFilePreviewBuilder.MAX_BYTES_PER_FILE));
        var previewed = IntStream.range(0, fileCount)
            .mapToObj(number -> stored("part-" + number + ".txt", "text/plain", full))
            .toArray(ObjectDescriptor[]::new);
        var late = new ObjectDescriptor("late-id", "late.txt", "text/plain", 10);
        var files = Arrays.copyOf(previewed, fileCount + 1);
        files[fileCount] = late;
        saved(files);

        var previews = this.builder.build(ATTEMPT_ID);

        assertThat(previews).hasSize(fileCount + 1);
        assertThat(previews.get(fileCount - 1).preview()).isNotNull();
        assertThat(previews.get(fileCount).preview()).isNull();
        assertThat(previews.get(fileCount).binary()).isFalse();
        verify(this.objectStorageService, never()).retrieve("late-id");
    }

    @Test
    void build_readsNothingForAnEmptyFile() {
        saved(new ObjectDescriptor("empty-id", "blank.md", "text/markdown", 0));

        var preview = this.builder.build(ATTEMPT_ID).get(0);

        assertThat(preview.preview()).isNull();
        assertThat(preview.binary()).isFalse();
        verify(this.objectStorageService, never()).retrieve(anyString());
    }
}
