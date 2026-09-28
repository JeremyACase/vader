package org.vader.core.server.service.agent.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskTitleMatcherTest {

    @Test
    void matches_exactTitle() {
        assertThat(TaskTitleMatcher.matches("Read Spreadsheet", "Read Spreadsheet")).isTrue();
    }

    @Test
    void matches_despiteCaseQuotesAndSpacing() {
        assertThat(TaskTitleMatcher.matches("Read Spreadsheet", "  \"read   SPREADSHEET\"  "))
            .isTrue();
        assertThat(TaskTitleMatcher.matches("Read Spreadsheet", "'Read Spreadsheet'")).isTrue();
    }

    @Test
    void doesNotMatch_differentTitle() {
        // Tolerant of how a title is repeated, never of which title it is.
        assertThat(TaskTitleMatcher.matches("Read Spreadsheet", "Read Spreadsheets")).isFalse();
        assertThat(TaskTitleMatcher.matches("Read Spreadsheet", "Spreadsheet")).isFalse();
    }

    @Test
    void doesNotMatch_null() {
        assertThat(TaskTitleMatcher.matches("Read Spreadsheet", null)).isFalse();
        assertThat(TaskTitleMatcher.matches(null, "Read Spreadsheet")).isFalse();
    }
}
