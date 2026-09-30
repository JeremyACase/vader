package org.vader.core.server.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.vader.core.server.sandbox.model.SandboxExecutionResult;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.taskagent.TaskAttemptToolContext;

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
        var definition = toolDefinitionNamed("run_python_code");

        assertThat(definition.inputSchema())
            .contains("\"code\"")
            .doesNotContain("sandboxName")
            .doesNotContain("taskAttemptId")
            .doesNotContain("toolContext")
            .doesNotContain("files");
    }

    @Test
    void uploadObject_uploadsFromTheSandboxOfTheAttemptNamedByTheToolContext() {
        var attemptId = UUID.randomUUID().toString();
        var expected = new ObjectDescriptor(
            UUID.randomUUID().toString(), "report.md", "text/markdown", 8L);
        when(this.service.uploadFile(attemptId, "report.md")).thenReturn(expected);

        var result = this.tools.uploadObject("report.md", TaskAttemptToolContext.of(attemptId));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void uploadObject_withoutAnAttemptInItsContext_refusesBeforeUploadingAnything() {
        var mcpContext = new ToolContext(Map.of("exchange", new Object()));

        assertThatThrownBy(() -> this.tools.uploadObject("report.md", mcpContext))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("task agent");
        verify(this.service, never()).uploadFile(anyString(), anyString());
    }

    @Test
    void uploadObject_exposesOnlyTheFilenameParameterToTheModel() {
        var definition = toolDefinitionNamed("upload_object");

        assertThat(definition.inputSchema())
            .contains("\"filename\"")
            .doesNotContain("taskAttemptId")
            .doesNotContain("toolContext")
            .doesNotContain("contentType");
    }

    private ToolDefinition toolDefinitionNamed(final String name) {
        var callbacks = MethodToolCallbackProvider.builder().toolObjects(this.tools).build()
            .getToolCallbacks();
        return Arrays.stream(callbacks)
            .map(ToolCallback::getToolDefinition)
            .filter(definition -> name.equals(definition.name()))
            .findFirst()
            .orElseThrow();
    }
}
