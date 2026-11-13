package org.vader.core.server.sandbox;

import static org.mockito.Mockito.mock;

import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.storage.ObjectMetadataRepository;
import org.vader.core.server.workflow.LiveSubtaskFilter;
import org.vader.core.server.workflow.TaskAttemptRepository;

/** Builds a {@link TaskWorkspaceFileResolver} wired to a real {@link LiveSubtaskFilter}. */
public final class TaskWorkspaceFileResolverObjectMother {

    private TaskWorkspaceFileResolverObjectMother() {
    }

    /**
     * A resolver that looks attempts and their saved files up in the given repositories.
     *
     * @param taskAttemptRepository where each task's latest attempt is found
     * @param objectMetadataRepository where each attempt's saved files are found
     * @return the wired resolver
     */
    public static TaskWorkspaceFileResolver over(
            final TaskAttemptRepository taskAttemptRepository,
            final ObjectMetadataRepository objectMetadataRepository) {
        var filter = new LiveSubtaskFilter();
        ReflectionTestUtils.setField(filter, "taskAttemptRepository", taskAttemptRepository);
        var resolver = new TaskWorkspaceFileResolver();
        ReflectionTestUtils.setField(resolver, "taskAttemptRepository", taskAttemptRepository);
        ReflectionTestUtils.setField(resolver, "liveSubtaskFilter", filter);
        ReflectionTestUtils.setField(
            resolver, "objectMetadataRepository", objectMetadataRepository);
        return resolver;
    }

    /**
     * A resolver for tasks whose upstream work saved no files.
     *
     * @param taskAttemptRepository where each task's latest attempt is found
     * @return the wired resolver
     */
    public static TaskWorkspaceFileResolver withoutUpstreamFiles(
            final TaskAttemptRepository taskAttemptRepository) {
        return over(taskAttemptRepository, mock(ObjectMetadataRepository.class));
    }
}
