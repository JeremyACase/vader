package org.vader.core.server.tools.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.tools.operators.ManagedResource;

@ExtendWith(MockitoExtension.class)
class PythonSandboxServiceTest {

    @Mock
    private PythonSandboxOperator operator;

    @InjectMocks
    private PythonSandboxService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(this.service, "namespace", "vader");
    }

    @Test
    void create_resolvesTheNameAndReturnsClusterAddress() {
        when(this.operator.reconcile(any(PythonSandboxSpec.class))).thenAnswer(invocation -> {
            PythonSandboxSpec spec = invocation.getArgument(0);
            return new ManagedResource(spec.name(), "vader", "Pending", Map.of());
        });

        var info = this.service.create("My Box");

        assertThat(info.name()).isEqualTo("vader-sandbox-my-box");
        assertThat(info.namespace()).isEqualTo("vader");
        assertThat(info.clusterAddress())
            .isEqualTo("vader-sandbox-my-box.vader.svc.cluster.local");

        ArgumentCaptor<PythonSandboxSpec> specCaptor =
            ArgumentCaptor.forClass(PythonSandboxSpec.class);
        verify(this.operator).reconcile(specCaptor.capture());
        assertThat(specCaptor.getValue().name()).isEqualTo("vader-sandbox-my-box");
    }

    @Test
    void list_mapsEveryManagedResource() {
        when(this.operator.list()).thenReturn(List.of(
            new ManagedResource("vader-sandbox-a", "vader", "Running", Map.of()),
            new ManagedResource("vader-sandbox-b", "vader", "Pending", Map.of())));

        var infos = this.service.list();

        assertThat(infos).extracting(SandboxInfo::name)
            .containsExactly("vader-sandbox-a", "vader-sandbox-b");
        assertThat(infos).extracting(SandboxInfo::phase)
            .containsExactly("Running", "Pending");
    }

    @Test
    void delete_delegatesToTheOperator() {
        this.service.delete("vader-sandbox-a");

        verify(this.operator).delete("vader-sandbox-a");
    }
}
