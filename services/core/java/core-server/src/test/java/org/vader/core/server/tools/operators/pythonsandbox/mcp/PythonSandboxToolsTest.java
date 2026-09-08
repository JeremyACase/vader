package org.vader.core.server.tools.operators.pythonsandbox.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vader.core.server.tools.operators.pythonsandbox.PythonSandboxService;
import org.vader.core.server.tools.operators.pythonsandbox.SandboxInfo;

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
}
