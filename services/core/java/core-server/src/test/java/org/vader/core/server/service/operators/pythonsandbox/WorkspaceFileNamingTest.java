package org.vader.core.server.service.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.models.WorkspaceFile;

class WorkspaceFileNamingTest {

    private static ObjectMetadataEntity fileNamed(final String id, final String originalFilename) {
        var file = new ObjectMetadataEntity();
        file.setId(id);
        file.setOriginalFilename(originalFilename);
        file.setContentType("application/octet-stream");
        return file;
    }

    private static ObjectMetadataEntity fileNamed(final String originalFilename) {
        return fileNamed(UUID.randomUUID().toString(), originalFilename);
    }

    @Test
    void assign_keepsAnOrdinaryFilenameAsIs() {
        var file = fileNamed("EP_Tactics.xlsx");

        var assigned = WorkspaceFileNaming.assign(List.of(file));

        assertThat(assigned).containsExactly(new WorkspaceFile(
            file.getId(), "EP_Tactics.xlsx", "application/octet-stream"));
    }

    @Test
    void assign_numbersDuplicateNamesBeforeTheirExtension() {
        var files = List.of(
            fileNamed("a", "report.xlsx"),
            fileNamed("b", "report.xlsx"),
            fileNamed("c", "report.xlsx"));

        var assigned = WorkspaceFileNaming.assign(files);

        assertThat(assigned).extracting(WorkspaceFile::filename)
            .containsExactly("report.xlsx", "report (2).xlsx", "report (3).xlsx");
    }

    @Test
    void assign_numbersDuplicateNameWithNoExtension() {
        var files = List.of(fileNamed("a", "README"), fileNamed("b", "README"));

        var assigned = WorkspaceFileNaming.assign(files);

        assertThat(assigned).extracting(WorkspaceFile::filename)
            .containsExactly("README", "README (2)");
    }

    @Test
    void assign_isOrderedByIdRegardlessOfInputOrder() {
        var first = fileNamed("a", "data.csv");
        var second = fileNamed("b", "data.csv");

        var forwards = WorkspaceFileNaming.assign(List.of(first, second));
        var backwards = WorkspaceFileNaming.assign(List.of(second, first));

        assertThat(forwards).isEqualTo(backwards);
        assertThat(forwards.get(0).objectMetadataId()).isEqualTo("a");
    }

    @Test
    void assign_flattensPathSeparatorsIntoOneSegment() {
        var assigned = WorkspaceFileNaming.assign(List.of(
            fileNamed("a", "reports/q3.xlsx"), fileNamed("b", "..\\evil.csv")));

        assertThat(assigned).extracting(WorkspaceFile::filename)
            .containsExactly("reports_q3.xlsx", ".._evil.csv");
    }

    @Test
    void assign_fallsBackToTheObjectIdForAnUnusableName() {
        var files = List.of(
            fileNamed("a", null), fileNamed("b", "  "), fileNamed("c", ".."));

        var assigned = WorkspaceFileNaming.assign(files);

        assertThat(assigned).extracting(WorkspaceFile::filename).containsExactly("a", "b", "c");
    }
}
