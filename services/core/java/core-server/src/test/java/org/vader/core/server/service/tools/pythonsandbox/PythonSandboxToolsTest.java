package org.vader.core.server.service.tools.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.service.operators.pythonsandbox.PythonSandboxService;

@ExtendWith(MockitoExtension.class)
class PythonSandboxToolsTest {

    @Mock
    private PythonSandboxService service;

    @InjectMocks
    private PythonSandboxTools tools;

    @Test
    void createSandbox_delegatesToTheService() {
        var info = new SandboxInfo("vader-sandbox-a", "vader", "Pending", "addr");
        when(this.service.create("a")).thenReturn(info);

        assertThat(this.tools.createSandbox("a")).isEqualTo(info);
    }

    @Test
    void listSandboxes_delegatesToTheService() {
        var infos = List.of(new SandboxInfo("vader-sandbox-a", "vader", "Running", "addr"));
        when(this.service.list()).thenReturn(infos);

        assertThat(this.tools.listSandboxes()).isEqualTo(infos);
    }

    @Test
    void deleteSandbox_delegatesAndConfirms() {
        var result = this.tools.deleteSandbox("vader-sandbox-a");

        verify(this.service).delete("vader-sandbox-a");
        assertThat(result).contains("vader-sandbox-a");
    }

    @Test
    void runPythonCode_delegatesToTheServiceWithCodeAndFiles() {
        var expected = new SandboxExecutionResult("hi\n", "", 0, false);
        when(this.service.runCode(eq("vader-sandbox-a"), any())).thenReturn(expected);
        var files = Map.of("data.csv", "YSxiCjEsMg==");

        var result = this.tools.runPythonCode("vader-sandbox-a", "print('hi')", files);

        assertThat(result).isEqualTo(expected);
        var requestCaptor = ArgumentCaptor.forClass(SandboxExecutionRequest.class);
        verify(this.service).runCode(eq("vader-sandbox-a"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().code()).isEqualTo("print('hi')");
        assertThat(requestCaptor.getValue().files()).isEqualTo(files);
    }
}
