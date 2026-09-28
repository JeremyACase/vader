package org.vader.core.server.service.tools.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.service.agent.task.TaskAttemptToolContext;
import org.vader.core.server.service.operators.pythonsandbox.TaskAttemptSandboxService;

@ExtendWith(MockitoExtension.class)
class TaskAttemptSandboxToolsTest {

    @Mock
    private TaskAttemptSandboxService service;

    @InjectMocks
    private TaskAttemptSandboxTools tools;

    @Test
    void runPythonCode_runsInTheSandboxOfTheAttemptNamedByTheToolContext() {
        var attemptId = UUID.randomUUID().toString();
        var expected = new SandboxExecutionResult("hi\n", "", 0, false);
        when(this.service.runCode(attemptId, "print('hi')")).thenReturn(expected);

        var result = this.tools.runPythonCode("print('hi')", TaskAttemptToolContext.of(attemptId));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void runPythonCode_withoutAnAttemptInItsContext_refusesBeforeRunningAnything() {
        // What an external MCP client's call looks like: a context, but no attempt in it.
        var mcpContext = new ToolContext(Map.of("exchange", new Object()));

        assertThatThrownBy(() -> this.tools.runPythonCode("pass", mcpContext))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("task agent");
        verify(this.service, never()).runCode(anyString(), anyString());
    }

    @Test
    void runPythonCode_exposesOnlyTheCodeParameterToTheModel() {
        var callbacks = MethodToolCallbackProvider.builder().toolObjects(this.tools).build()
            .getToolCallbacks();

        assertThat(callbacks).hasSize(1);
        var definition = callbacks[0].getToolDefinition();
        assertThat(definition.name()).isEqualTo("run_python_code");
        assertThat(definition.inputSchema())
            .contains("\"code\"")
            .doesNotContain("sandboxName")
            .doesNotContain("taskAttemptId")
            .doesNotContain("toolContext")
            .doesNotContain("files");
    }
}
