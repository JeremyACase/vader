package org.vader.core.server.service.operators.pythonsandbox;

import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;

/**
 * Proxies a code-execution request to one Python sandbox pod's own HTTP server
 * ({@code core-python-sandbox-server}), reached over the Service
 * {@link org.vader.core.server.service.builders.pythonsandbox.PythonSandboxManifestBuilder}
 * provisions for it -- never via the Kubernetes API, unlike the rest of this operator.
 *
 * <p>The {@link RestClient} is injected rather than built here (see
 * {@link org.vader.core.server.service.config.pythonsandbox.SandboxExecutionClientConfig}) --
 * it must be pinned to HTTP/1.1, since the default JDK-HttpClient-backed client attempts an
 * HTTP/2 cleartext upgrade that uvicorn (the ASGI server {@code core-python-sandbox-server} runs
 * on) doesn't understand, silently corrupting every request.</p>
 */
@Component
public class SandboxExecutionClient {

    private static final int EXEC_PORT = 8888;
    private static final String EXECUTE_URI =
        "http://{name}.{namespace}.svc.cluster.local:{port}/execute";
    private static final String STAGE_FILE_URI =
        "http://{name}.{namespace}.svc.cluster.local:{port}/workspace/files/{filename}";

    @Autowired
    private RestClient sandboxExecutionRestClient;

    @Value("${vader.kubernetes.namespace:default}")
    private String namespace;

    /**
     * Runs code inside the named sandbox.
     *
     * @param sandboxName the exact sandbox name
     * @param request the code (and any files) to run
     * @return the run's stdout/stderr/exit code
     * @throws SandboxExecutionException if the sandbox is unreachable or returns an error
     */
    public SandboxExecutionResult execute(
        final String sandboxName, final SandboxExecutionRequest request) {

        SandboxExecutionResult result;
        try {
            result = this.sandboxExecutionRestClient.post()
                .uri(EXECUTE_URI, sandboxName, this.namespace, EXEC_PORT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(this.withoutNullFiles(request))
                .retrieve()
                .body(SandboxExecutionResult.class);
        } catch (RestClientException e) {
            throw new SandboxExecutionException(
                "Could not run code in sandbox '" + sandboxName + "': " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Writes raw bytes straight into a sandbox's persistent workspace, bypassing the JSON/
     * base64 {@code files} path {@link #execute} uses -- the caller (a stage-object request that
     * ultimately came from a model's tool call) never has to hold the content itself, only the
     * confirmation this returns nothing more than.
     *
     * @param sandboxName the exact sandbox name
     * @param filename the name to stage the content under inside the sandbox's workspace
     * @param content the raw bytes to write
     * @throws SandboxExecutionException if the sandbox is unreachable or returns an error
     */
    public void stageFile(final String sandboxName, final String filename, final byte[] content) {
        try {
            this.sandboxExecutionRestClient.put()
                .uri(STAGE_FILE_URI, sandboxName, this.namespace, EXEC_PORT, filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            throw new SandboxExecutionException(
                "Could not stage '" + filename + "' into sandbox '" + sandboxName + "': "
                    + e.getMessage(), e);
        }
    }

    /**
     * A caller (e.g. a REST body that omits {@code files} entirely) may leave {@code files}
     * {@code null}. Jackson serializes that as a literal JSON {@code null}, which the sandbox's
     * Pydantic model rejects outright -- its own default only fills in a key that is absent, not
     * one present with a null value -- so this is normalized to an empty map before it ever
     * reaches the wire.
     */
    private SandboxExecutionRequest withoutNullFiles(final SandboxExecutionRequest request) {
        return new SandboxExecutionRequest(
            request.code(),
            Objects.requireNonNullElse(request.files(), Map.of()),
            request.timeoutSeconds());
    }
}
