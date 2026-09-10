package org.vader.core.server.service.io;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.core.server.service.WorkflowService;

class ClientPromptInboxTest {

    private static final String PROMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private WorkflowService workflowService;
    private TaskExecutor executor;
    private ClientPromptInbox inbox;

    @BeforeEach
    void setUp() {
        this.workflowService = mock(WorkflowService.class);
        this.executor = mock(TaskExecutor.class);
        this.inbox = new ClientPromptInbox();
        ReflectionTestUtils.setField(this.inbox, "workflowService", this.workflowService);
        ReflectionTestUtils.setField(this.inbox, "executor", this.executor);
    }

    private static ClientPromptOutboxMessageEntity messageForPrompt() {
        var prompt = new ClientPromptEntity();
        prompt.setId(PROMPT_ID);
        var message = new ClientPromptOutboxMessageEntity();
        message.setClientPrompt(prompt);
        return message;
    }

    @Test
    void handle_decomposesThePromptCarriedByTheMessage() {
        this.inbox.handle(messageForPrompt());

        verify(this.workflowService).decompose(PROMPT_ID);
    }

    @Test
    void onEnqueued_forClientPromptType_kicksDrainOntoExecutor() {
        this.inbox.onEnqueued(new OutboxMessageEnqueuedEvent("ClientPrompt"));

        verify(this.executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void onEnqueued_forAnotherModelType_isIgnored() {
        this.inbox.onEnqueued(new OutboxMessageEnqueuedEvent("SomethingElse"));

        verify(this.executor, never()).execute(org.mockito.ArgumentMatchers.any());
    }
}
