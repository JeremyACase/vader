package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.config.VaderMode;
import org.vader.core.server.mcp.McpToolCallbackRegistry;
import org.vader.core.server.orchestration.DecompositionLlmExecutor;
import org.vader.core.server.orchestration.TaskPlanRefinementLlmExecutor;
import org.vader.core.server.orchestration.model.DecompositionRequest;
import org.vader.core.server.orchestration.model.TaskPlanRefinementRequest;
import org.vader.core.server.review.EvaluationLlmExecutor;
import org.vader.core.server.review.ReattemptDecisionLlmExecutor;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.ReattemptDecisionRequest;
import org.vader.core.server.taskagent.InferenceTurnLlmExecutor;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.workflow.WorkflowSynthesisLlmExecutor;
import org.vader.core.server.workflow.model.TaskOutcome;
import org.vader.core.server.workflow.model.WorkflowSynthesisRequest;

/**
 * Runs every real executor -- its prompt, its structured-output parsing -- against the stub, so a
 * change to either side that would break the devops test pipeline fails here first.
 */
class ScriptedChatModelStubTest {

    private ChatClient.Builder chatClientBuilder;
    private McpToolCallbackRegistry toolCallbackRegistry;

    @BeforeEach
    void setUp() {
        var stub = new ScriptedChatModelStub();
        ReflectionTestUtils.setField(stub, "mode", VaderMode.TEST);
        this.chatClientBuilder = ChatClient.builder(stub);
        this.toolCallbackRegistry = mock(McpToolCallbackRegistry.class);
        when(this.toolCallbackRegistry.forAudience(any())).thenReturn(List.of());
    }

    private <E> E wired(final E executor) {
        ReflectionTestUtils.setField(executor, "chatClientBuilder", this.chatClientBuilder);
        return executor;
    }

    private <E> E wiredWithTools(final E executor) {
        ReflectionTestUtils.setField(executor, "toolCallbackRegistry", this.toolCallbackRegistry);
        return this.wired(executor);
    }

    private static EvaluationRequest evaluationOf(final TaskAttemptStatus status) {
        return new EvaluationRequest(
            "title", "description", status, "result", "reason", List.of(), null);
    }

    private static ConversationMessage message(final ConversationRole role, final String content) {
        return new ConversationMessage(role, content, null, null, null);
    }

    @Test
    void decomposition_parsesIntoTheFourTaskBirthdayPlan() {
        var executor = this.wiredWithTools(new DecompositionLlmExecutor());

        var plan = executor.execute(new DecompositionRequest("anything at all", null));

        assertThat(plan.objective()).contains("birthday party");
        assertThat(plan.tasks()).hasSize(4)
            .allSatisfy(task -> assertThat(task.dependsOn()).isEmpty());
    }

    @Test
    void refinement_approvesThePlan() {
        var task = new Task();
        task.setTitle("Book venue");
        task.setDescription("Find a place");
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(List.of(task));
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("throw a party");
        taskPlan.setTaskGraph(taskGraph);
        var executor = this.wired(new TaskPlanRefinementLlmExecutor());

        var verdict = executor.execute(new TaskPlanRefinementRequest("a party", taskPlan));

        assertThat(verdict.needsRevision()).isFalse();
        assertThat(verdict.missingDependencies()).isEmpty();
    }

    @Test
    void evaluation_passesAnAttemptThatReportedSuccess() {
        var executor = this.wired(new EvaluationLlmExecutor());

        var verdict = executor.execute(evaluationOf(TaskAttemptStatus.SUCCEEDED));

        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.remainingSubtasks()).isEmpty();
    }

    @Test
    void evaluation_failsAnAttemptThatReportedFailure() {
        var executor = this.wired(new EvaluationLlmExecutor());

        var verdict = executor.execute(evaluationOf(TaskAttemptStatus.FAILED));

        assertThat(verdict.passed()).isFalse();
    }

    @Test
    void reattemptDecision_alwaysRetries() {
        var executor = this.wired(new ReattemptDecisionLlmExecutor());

        var decision = executor.execute(
            new ReattemptDecisionRequest("title", "description", 1, 3, "it broke", List.of()));

        assertThat(decision.shouldReattempt()).isTrue();
    }

    @Test
    void synthesis_answersInPlainText() {
        var executor = this.wired(new WorkflowSynthesisLlmExecutor());

        var answer = executor.execute(new WorkflowSynthesisRequest(
            "a party", "throw a party", List.of(new TaskOutcome("Book venue", true, "Booked."))));

        assertThat(answer).contains("Scripted synthesis");
    }

    @Test
    void inferenceTurn_firstRequestsTheScriptedTool() {
        var executor = this.wiredWithTools(new InferenceTurnLlmExecutor());

        var turn = executor.execute(List.of(message(ConversationRole.USER, "do the task")));

        assertThat(turn.toolCalls()).singleElement()
            .satisfies(call -> assertThat(call.name())
                .isEqualTo(ScriptedChatModelStub.SCRIPTED_TOOL_NAME));
        assertThat(turn.tokensSpent()).isPositive();
        assertThat(turn.finishReason()).isEqualTo("stop");
    }

    @Test
    void inferenceTurn_afterToolResultsGivesTheFinalAnswer() {
        var executor = this.wiredWithTools(new InferenceTurnLlmExecutor());
        var toolResult = new ConversationMessage(ConversationRole.TOOL, "[\"Task\"]",
            null, "scripted-call-1", ScriptedChatModelStub.SCRIPTED_TOOL_NAME);

        var turn = executor.execute(
            List.of(message(ConversationRole.USER, "do the task"), toolResult));

        assertThat(turn.content()).isEqualTo(ScriptedChatModelStub.INFERENCE_ANSWER);
        assertThat(turn.toolCalls()).isEmpty();
    }

    @Test
    void requireTestMode_refusesEveryOtherMode() {
        var stub = new ScriptedChatModelStub();
        ReflectionTestUtils.setField(stub, "mode", VaderMode.PROD);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(stub, "requireTestMode"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("vader.mode=TEST");
    }

    @Test
    void requireTestMode_allowsTestMode() {
        var stub = new ScriptedChatModelStub();
        ReflectionTestUtils.setField(stub, "mode", VaderMode.TEST);

        assertThatNoException()
            .isThrownBy(() -> ReflectionTestUtils.invokeMethod(stub, "requireTestMode"));
    }
}
