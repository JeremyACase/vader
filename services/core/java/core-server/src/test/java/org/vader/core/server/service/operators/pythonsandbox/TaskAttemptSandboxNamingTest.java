package org.vader.core.server.service.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskAttemptSandboxNamingTest {

    @Test
    void resolve_isStableForTheSameAttempt() {
        var attemptId = UUID.randomUUID().toString();

        assertThat(TaskAttemptSandboxNaming.resolve(attemptId))
            .isEqualTo(TaskAttemptSandboxNaming.resolve(attemptId));
    }

    @Test
    void resolve_differsBetweenAttempts() {
        assertThat(TaskAttemptSandboxNaming.resolve(UUID.randomUUID().toString()))
            .isNotEqualTo(TaskAttemptSandboxNaming.resolve(UUID.randomUUID().toString()));
    }

    @Test
    void resolve_isValidDnsLabelForUuidAttemptId() {
        var name = TaskAttemptSandboxNaming.resolve(UUID.randomUUID().toString());

        assertThat(name)
            .startsWith("vader-sandbox-attempt-")
            .matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")
            .hasSizeLessThanOrEqualTo(63);
    }
}
