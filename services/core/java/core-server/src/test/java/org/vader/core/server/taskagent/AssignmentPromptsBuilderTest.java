package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.llm.PromptKey;
import org.vader.core.server.llm.PromptTemplateRegistry;
import org.vader.core.server.llm.PromptTemplateRegistryObjectMother;

class AssignmentPromptsBuilderTest {

    private AssignmentContextBuilder assignmentContextBuilder;

    private PromptTemplateRegistry promptTemplateRegistry;

    private AssignmentPromptsBuilder builder;

    @BeforeEach
    void setUp() {
        this.assignmentContextBuilder = mock(AssignmentContextBuilder.class);
        this.promptTemplateRegistry = PromptTemplateRegistryObjectMother.loaded();
        this.builder = new AssignmentPromptsBuilder();
        ReflectionTestUtils.setField(
            this.builder, "assignmentContextBuilder", this.assignmentContextBuilder);
        ReflectionTestUtils.setField(
            this.builder, "promptTemplateRegistry", this.promptTemplateRegistry);
    }

    @Test
    void build_opensWithTheContextThenTheTask() {
        var task = new TaskEntity();
        task.setDescription(Instancio.create(String.class));
        var context = Instancio.create(String.class);
        when(this.assignmentContextBuilder.build(task)).thenReturn(context);

        var prompts = this.builder.build(task);

        assertThat(prompts.task())
            .startsWith(context)
            .endsWith("Your task: " + task.getDescription());
    }

    @Test
    void build_carriesTheSystemPromptAndEveryNudge() {
        var task = new TaskEntity();
        when(this.assignmentContextBuilder.build(task)).thenReturn("context");

        var prompts = this.builder.build(task);

        assertThat(prompts.system()).isEqualTo(
            this.promptTemplateRegistry.render(PromptKey.TASK_SYSTEM));
        assertThat(prompts.system()).contains("save it with upload_object");
        assertThat(prompts.nudges().emptyReply()).isEqualTo(
            this.promptTemplateRegistry.render(PromptKey.EMPTY_REPLY_NUDGE));
        assertThat(prompts.nudges().unexecutedCode()).isEqualTo(
            this.promptTemplateRegistry.render(PromptKey.UNEXECUTED_CODE_NUDGE));
        assertThat(prompts.nudges().unresolvedError()).isEqualTo(
            this.promptTemplateRegistry.render(PromptKey.UNRESOLVED_ERROR_NUDGE));
    }
}
