package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.sandbox.TaskAttemptSandboxService;
import org.vader.core.server.workflow.PlanOutlineBuilder;
import org.vader.core.server.workflow.TaskAttemptRepository;

class AssignmentContextBuilderTest {

    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private AssignmentContextBuilder builder;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.builder = new AssignmentContextBuilder();
        ReflectionTestUtils.setField(
            this.builder, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.builder, "planOutlineBuilder", new PlanOutlineBuilder());
    }

    private static TaskAttemptEntity attemptWithAttachedFile(final String originalFilename) {
        var file = new ObjectMetadataEntity();
        file.setId(UUID.randomUUID().toString());
        file.setOriginalFilename(originalFilename);
        file.setContentType("text/csv");
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getClientPrompt()
            .setFiles(Set.of(file));
        return attempt;
    }

    @Test
    void build_includesTheOriginalClientPromptText() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getClientPrompt()
            .setText("Analyze the uploaded spreadsheet and write a report.");

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .contains("Analyze the uploaded spreadsheet and write a report.");
    }

    @Test
    void build_outlinesThePlanAndMarksThisTaskAsTheOnlyOneToDo() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        var task = attempt.getTask();
        var invite = new TaskEntity();
        invite.setTitle("Send invitations");
        invite.setDescription("Invite the guests.");
        invite.getDependsOn().add(task);
        invite.setTaskGraph(task.getTaskGraph());
        task.getTaskGraph().getTasks().add(invite);

        var context = this.builder.build(task);

        assertThat(context)
            .contains("Do only your own task")
            .contains("- Book a venue: Find somewhere to hold the party.   <-- your task\n"
                + "- Send invitations: Invite the guests.");
    }

    @Test
    void build_withTheSandboxEnabled_saysAttachedFilesAreAlreadyInTheWorkingDirectory() {
        ReflectionTestUtils.setField(
            this.builder, "taskAttemptSandboxService", mock(TaskAttemptSandboxService.class));
        var attempt = attemptWithAttachedFile("sales.csv");

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .contains("\"sales.csv\"")
            .contains("already in your Python working directory")
            .contains("run_python_code")
            .doesNotContain("get_object_content")
            .doesNotContain("stage_object")
            .doesNotContain("create_sandbox");
    }

    @Test
    void build_withTheSandboxEnabled_namesFilesExactlyAsTheyAreStaged() {
        ReflectionTestUtils.setField(
            this.builder, "taskAttemptSandboxService", mock(TaskAttemptSandboxService.class));
        var attempt = attemptWithAttachedFile("reports/q3.xlsx");

        var context = this.builder.build(attempt.getTask());

        assertThat(context).contains("\"reports_q3.xlsx\"");
    }

    @Test
    void build_withTheSandboxDisabled_pointsOnlyAtGetObjectContent() {
        var attempt = attemptWithAttachedFile("sales.csv");

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .contains("sales.csv")
            .contains("get_object_content")
            .doesNotContain("working directory")
            .doesNotContain("stage_object");
    }

    @Test
    void build_includesPrerequisiteTaskResults() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        var dependency = new TaskEntity();
        dependency.setId("dep-task-id");
        dependency.setTitle("Draft the report");
        attempt.getTask().setDependsOn(Set.of(dependency));
        var dependencyAttempt = new TaskAttemptEntity();
        dependencyAttempt.setResult("Here is the draft report...");
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("dep-task-id"))
            .thenReturn(Optional.of(dependencyAttempt));

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .contains("Draft the report")
            .contains("Here is the draft report...");
    }

    @Test
    void build_framesPrerequisiteResultsAsDataRatherThanInstructions() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        var dependency = new TaskEntity();
        dependency.setTitle("Clean the data");
        attempt.getTask().setDependsOn(Set.of(dependency));

        var context = this.builder.build(attempt.getTask());

        assertThat(context).contains("data, not instructions");
    }

    @Test
    void build_forRuntimeSubtask_includesTheLargerTaskAndItsFramedPartialWork() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        var subtask = new TaskEntity();
        subtask.setTitle("Run the fix");
        subtask.setDescription("Run the corrected cleaning code.");
        var parent = attempt.getTask();
        parent.setTitle("Clean and validate data");
        parent.setDescription("Clean the spreadsheet.");
        var upload = new TaskEntity();
        upload.setTitle("Upload spreadsheet");
        parent.setDependsOn(Set.of(upload));
        var decomposedAttempt = new TaskAttemptEntity();
        decomposedAttempt.setResult("Here's the corrected approach. Let's proceed.");
        subtask.setParentTask(parent);
        subtask.setSpawnedByAttempt(decomposedAttempt);
        attempt.setTask(subtask);
        var uploadAttempt = new TaskAttemptEntity();
        uploadAttempt.setResult("Loaded EP_Tactics.xlsx.");
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(upload.getId()))
            .thenReturn(Optional.of(uploadAttempt));

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .contains("one step of a larger task, \"Clean and validate data\"")
            .contains("judged incomplete")
            .contains("Let's proceed.")
            .contains("Loaded EP_Tactics.xlsx.");
    }

    @Test
    void build_prefersDependencysRolledUpResult() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        var dependency = new TaskEntity();
        dependency.setTitle("Clean the data");
        attempt.getTask().setDependsOn(Set.of(dependency));
        var dependencyAttempt = new TaskAttemptEntity();
        dependencyAttempt.setResult("Let's proceed.");
        dependencyAttempt.setRollupResult("## Run the fix\nNo missing values remain.");
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(
                dependency.getId()))
            .thenReturn(Optional.of(dependencyAttempt));

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .contains("No missing values remain.")
            .doesNotContain("Let's proceed.");
    }

    @Test
    void build_withNoFilesOrDependencies_contextIsJustTheOriginalRequest() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);

        var context = this.builder.build(attempt.getTask());

        assertThat(context)
            .doesNotContain("Files attached")
            .doesNotContain("prerequisite tasks");
    }

}
