package org.vader.core.server.taskagent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.llm.PromptKey;
import org.vader.core.server.llm.PromptTemplateRegistry;
import org.vader.core.server.llm.PromptVariablesBuilder;
import org.vader.core.server.taskagent.model.AssignmentNudges;
import org.vader.core.server.taskagent.model.AssignmentPrompts;

/**
 * Renders every prompt a harness needs for one task, so the harness carries no prompt text of
 * its own: the opening messages, with the task's background from
 * {@link AssignmentContextBuilder}, and the nudges it sends back when a reply can't be accepted.
 */
@Component
public class AssignmentPromptsBuilder {

    @Autowired
    private AssignmentContextBuilder assignmentContextBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

    /**
     * Builds the prompts for one task about to run.
     *
     * @param task the task about to be dispatched
     * @return its prompts
     */
    public AssignmentPrompts build(final TaskEntity task) {
        var taskMessage = this.promptTemplateRegistry.render(PromptKey.TASK_MESSAGE,
            new PromptVariablesBuilder()
                .with("context", this.assignmentContextBuilder.build(task))
                .with("objective", task.getDescription())
                .build());
        var nudges = new AssignmentNudges(
            this.promptTemplateRegistry.render(PromptKey.EMPTY_REPLY_NUDGE),
            this.promptTemplateRegistry.render(PromptKey.UNEXECUTED_CODE_NUDGE),
            this.promptTemplateRegistry.render(PromptKey.UNRESOLVED_ERROR_NUDGE));
        return new AssignmentPrompts(
            this.promptTemplateRegistry.render(PromptKey.TASK_SYSTEM), taskMessage, nudges);
    }
}
