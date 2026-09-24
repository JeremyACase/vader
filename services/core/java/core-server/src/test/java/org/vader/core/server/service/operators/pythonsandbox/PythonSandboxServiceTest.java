package org.vader.core.server.service.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.models.ManagedResource;
import org.vader.core.server.models.PythonSandboxSpec;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.service.storage.ObjectContent;
import org.vader.core.server.service.storage.ObjectStorageService;

@ExtendWith(MockitoExtension.class)
class PythonSandboxServiceTest {

    @Mock
    private PythonSandboxOperator operator;

    @Mock
    private SandboxExecutionClient executionClient;

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private PythonSandboxService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(this.service, "namespace", "vader");
        // Zeroed so awaitReady's retry loop, when it runs, does not actually sleep in tests.
        ReflectionTestUtils.setField(this.service, "readyPollIntervalMs", 0L);
        ReflectionTestUtils.setField(this.service, "readyPollMaxAttempts", 3);
    }

    @Test
    void create_resolvesTheNameAndReturnsClusterAddressOnceAlreadyReady() {
        when(this.operator.reconcile(any(PythonSandboxSpec.class))).thenAnswer(invocation -> {
            PythonSandboxSpec spec = invocation.getArgument(0);
            return new ManagedResource(spec.name(), "vader", "Running", Map.of());
        });

        var info = this.service.create("My Box");

        assertThat(info.name()).isEqualTo("vader-sandbox-my-box");
        assertThat(info.namespace()).isEqualTo("vader");
        assertThat(info.phase()).isEqualTo("Running");
        assertThat(info.clusterAddress())
            .isEqualTo("vader-sandbox-my-box.vader.svc.cluster.local");

        ArgumentCaptor<PythonSandboxSpec> specCaptor =
            ArgumentCaptor.forClass(PythonSandboxSpec.class);
        verify(this.operator).reconcile(specCaptor.capture());
        assertThat(specCaptor.getValue().name()).isEqualTo("vader-sandbox-my-box");
    }

    @Test
    void create_pollsUntilTheSandboxBecomesReady() {
        when(this.operator.reconcile(any(PythonSandboxSpec.class)))
            .thenReturn(new ManagedResource("vader-sandbox-my-box", "vader", "Pending", Map.of()))
            .thenReturn(new ManagedResource("vader-sandbox-my-box", "vader", "Pending", Map.of()))
            .thenReturn(new ManagedResource("vader-sandbox-my-box", "vader", "Running", Map.of()));

        var info = this.service.create("My Box");

        assertThat(info.phase()).isEqualTo("Running");
        verify(this.operator, times(3)).reconcile(any(PythonSandboxSpec.class));
    }

    @Test
    void create_givesUpAfterTheMaxAttemptsAndReturnsWhateverPhaseItLastSaw() {
        when(this.operator.reconcile(any(PythonSandboxSpec.class)))
            .thenReturn(new ManagedResource("vader-sandbox-my-box", "vader", "Pending", Map.of()));

        var info = this.service.create("My Box");

        assertThat(info.phase()).isEqualTo("Pending");
        // The initial reconcile, plus one retry per configured attempt (readyPollMaxAttempts=3).
        verify(this.operator, times(4)).reconcile(any(PythonSandboxSpec.class));
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

    @Test
    void runCode_delegatesToTheExecutionClient() {
        var request = new SandboxExecutionRequest("print('hi')", Map.of(), null);
        var expected = new SandboxExecutionResult("hi\n", "", 0, false);
        when(this.executionClient.execute("vader-sandbox-a", request)).thenReturn(expected);

        var result = this.service.runCode("vader-sandbox-a", request);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void stageObject_fetchesTheObjectAndStagesItsRawBytesIntoTheSandbox() {
        var bytes = "a,b\n1,2\n".getBytes();
        var content = new ObjectContent(
            new ByteArrayResource(bytes), "sales.csv", "text/csv", bytes.length);
        when(this.objectStorageService.retrieve("obj-1")).thenReturn(content);

        var info = this.service.stageObject("vader-sandbox-a", "obj-1", null);

        assertThat(info.filename()).isEqualTo("sales.csv");
        assertThat(info.contentType()).isEqualTo("text/csv");
        assertThat(info.size()).isEqualTo(bytes.length);
        verify(this.executionClient).stageFile("vader-sandbox-a", "sales.csv", bytes);
    }

    @Test
    void stageObject_withAnExplicitFilename_stagesUnderThatNameInstead() {
        var bytes = "x".getBytes();
        var content = new ObjectContent(
            new ByteArrayResource(bytes), "original.bin", "application/octet-stream",
            bytes.length);
        when(this.objectStorageService.retrieve("obj-1")).thenReturn(content);

        var info = this.service.stageObject("vader-sandbox-a", "obj-1", "renamed.bin");

        assertThat(info.filename()).isEqualTo("renamed.bin");
        verify(this.executionClient).stageFile("vader-sandbox-a", "renamed.bin", bytes);
    }

    @Test
    void ensureReady_usesTheNameVerbatimRatherThanResolvingIt() {
        when(this.operator.reconcile(any(PythonSandboxSpec.class))).thenAnswer(invocation -> {
            PythonSandboxSpec spec = invocation.getArgument(0);
            return new ManagedResource(spec.name(), "vader", "Running", Map.of());
        });

        var info = this.service.ensureReady("vader-sandbox-attempt-abc");

        assertThat(info.name()).isEqualTo("vader-sandbox-attempt-abc");
        assertThat(info.phase()).isEqualTo("Running");
    }

    @Test
    void stageObjectIfAbsent_whenAlreadyStaged_neverFetchesOrSendsTheObject() {
        when(this.executionClient.isStaged("vader-sandbox-a", "sales.csv")).thenReturn(true);

        this.service.stageObjectIfAbsent("vader-sandbox-a", "obj-1", "sales.csv");

        verify(this.objectStorageService, never()).retrieve(any());
        verify(this.executionClient, never()).stageFile(any(), any(), any());
    }

    @Test
    void stageObjectIfAbsent_whenMissing_stagesItUnderTheGivenName() {
        var bytes = "a,b\n1,2\n".getBytes();
        when(this.executionClient.isStaged("vader-sandbox-a", "sales.csv")).thenReturn(false);
        when(this.objectStorageService.retrieve("obj-1")).thenReturn(new ObjectContent(
            new ByteArrayResource(bytes), "original.csv", "text/csv", bytes.length));

        this.service.stageObjectIfAbsent("vader-sandbox-a", "obj-1", "sales.csv");

        verify(this.executionClient).stageFile("vader-sandbox-a", "sales.csv", bytes);
    }
}
