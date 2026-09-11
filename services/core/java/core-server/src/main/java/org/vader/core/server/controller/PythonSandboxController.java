package org.vader.core.server.controller;

import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.service.operators.pythonsandbox.PythonSandboxService;

/**
 * REST surface for the Python sandbox operator, used by the Helm smoke test and for manual
 * debugging. It shares {@link PythonSandboxService} with the MCP tools, so both entry points
 * behave identically. Registered only when the operator is enabled.
 */
@RestController
@RequestMapping("/vader/core-server/python-sandbox/sandboxes")
@ConditionalOnProperty(
    prefix = "vader.operators.python-sandbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class PythonSandboxController {

    private static final Logger logger = LoggerFactory.getLogger(PythonSandboxController.class);

    @Autowired
    private PythonSandboxService service;

    /**
     * Creates a sandbox.
     *
     * @param request an optional body carrying a friendly name
     * @return the created sandbox
     */
    @PostMapping
    public ResponseEntity<SandboxInfo> create(
        @RequestBody(required = false) final CreateRequest request) {
        var name = request == null ? null : request.name();
        return ResponseEntity.ok(this.service.create(name));
    }

    /**
     * Lists every managed sandbox.
     *
     * @return the sandboxes
     */
    @GetMapping
    public ResponseEntity<List<SandboxInfo>> list() {
        return ResponseEntity.ok(this.service.list());
    }

    /**
     * Deletes a sandbox by name.
     *
     * @param name the exact sandbox name
     * @return an empty 204 response
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable final String name) {
        this.service.delete(name);
        return ResponseEntity.noContent().build();
    }

    /**
     * Maps a Kubernetes API failure to a 502, since the failure originates upstream of this
     * service rather than in the caller's request.
     *
     * @param exception the Kubernetes client failure
     * @return a 502 response describing the failure
     */
    @ExceptionHandler(KubernetesClientException.class)
    public ResponseEntity<Map<String, String>> handleKubernetes(
        final KubernetesClientException exception) {
        logger.warn("Kubernetes call failed: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "kubernetes_error", "message", exception.getMessage()));
    }

    /**
     * Optional request body for sandbox creation.
     *
     * @param name a friendly name for the sandbox
     */
    public record CreateRequest(String name) {
    }
}
