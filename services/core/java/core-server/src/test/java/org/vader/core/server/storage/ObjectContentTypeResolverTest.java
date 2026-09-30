package org.vader.core.server.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObjectContentTypeResolverTest {

    @Test
    void forFilename_fillsInTextFormatsSpringHasNoEntryFor() {
        assertThat(ObjectContentTypeResolver.forFilename("report.md")).isEqualTo("text/markdown");
        assertThat(ObjectContentTypeResolver.forFilename("REPORT.MD")).isEqualTo("text/markdown");
        assertThat(ObjectContentTypeResolver.forFilename("config.yml"))
            .isEqualTo("application/yaml");
    }

    @Test
    void forFilename_otherwiseUsesSpringsOwnTable() {
        assertThat(ObjectContentTypeResolver.forFilename("data.csv")).isEqualTo("text/csv");
        assertThat(ObjectContentTypeResolver.forFilename("results.json"))
            .isEqualTo("application/json");
        assertThat(ObjectContentTypeResolver.forFilename("tactics.xlsx"))
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void forFilename_withNoRecognizableExtension_isOctetStream() {
        assertThat(ObjectContentTypeResolver.forFilename("blob.unknownext"))
            .isEqualTo("application/octet-stream");
        assertThat(ObjectContentTypeResolver.forFilename("no_extension"))
            .isEqualTo("application/octet-stream");
    }
}
