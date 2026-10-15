package org.vader.core.server.workflow;

import static org.mockito.Mockito.mock;

import org.springframework.test.util.ReflectionTestUtils;

/** Builds a {@link PlanOutlineBuilder} wired to a real {@link LiveSubtaskFilter} for unit tests. */
public final class PlanOutlineBuilderObjectMother {

    private PlanOutlineBuilderObjectMother() {
    }

    /**
     * A builder whose subtask filter looks up attempts in the given repository.
     *
     * @param taskAttemptRepository where each parent's latest attempt is found
     * @return the wired builder
     */
    public static PlanOutlineBuilder over(final TaskAttemptRepository taskAttemptRepository) {
        var filter = new LiveSubtaskFilter();
        ReflectionTestUtils.setField(filter, "taskAttemptRepository", taskAttemptRepository);
        var builder = new PlanOutlineBuilder();
        ReflectionTestUtils.setField(builder, "liveSubtaskFilter", filter);
        return builder;
    }

    /**
     * A builder for plans with no runtime subtasks, where no attempt is ever looked up.
     *
     * @return the wired builder
     */
    public static PlanOutlineBuilder withoutAttempts() {
        return over(mock(TaskAttemptRepository.class));
    }
}
