package org.vader.core.server.sandbox;

import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.vader.core.server.sandbox.model.SandboxExecutionRequest;
import org.vader.core.server.sandbox.model.SandboxExecutionResult;

/**
 * Proxies a code-execution request to one Python sandbox pod's own HTTP server
 * ({@code core-python-sandbox-server}), reached over the Service
 * {@link org.vader.core.server.sandbox.PythonSandboxManifestBuilder}
 * provisions for it -- never via the Kubernetes API, unlike the rest of this operator.
 *
 * <p>The {@link RestClient} is injected, pinned to HTTP/1.1, from
 * {@link org.vader.core.server.sandbox.SandboxExecutionClientConfig}.</p>
 */
@Component
public class SandboxExecutionClient {

    private static final int EXEC_PORT = 8888;

    private static final int MAX_SANDBOX_NAME_IN_ERROR = 80;
    private static final String EXECUTE_URI =
        "http://{name}.{namespace}.svc.cluster.local:{port}/execute";
    private static final String HEALTH_URI =
        "http://{name}.{namespace}.svc.cluster.local:{port}/health";
    private static final String WORKSPACE_FILE_URI =
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
        } catch (IllegalArgumentException e) {
            throw invalidSandboxName(sandboxName, e);
        } catch (RestClientException e) {
            throw new SandboxExecutionException(
                "Could not run code in sandbox '" + sandboxName + "': " + describe(e), e);
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
                .uri(WORKSPACE_FILE_URI, sandboxName, this.namespace, EXEC_PORT, filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .toBodilessEntity();
        } catch (IllegalArgumentException e) {
            throw invalidSandboxName(sandboxName, e);
        } catch (RestClientException e) {
            throw new SandboxExecutionException(
                "Could not stage '" + filename + "' into sandbox '" + sandboxName + "': "
                    + describe(e), e);
        }
    }

    /**
     * Reads a file's raw bytes back out of a sandbox's persistent workspace -- the reverse of
     * {@link #stageFile}, for a file the sandbox's own code wrote.
     *
     * @param sandboxName the exact sandbox name
     * @param filename the file's path relative to the sandbox's workspace
     * @return the file's raw bytes
     * @throws SandboxExecutionException if there is no such file, or the sandbox is unreachable
     *     or returns an error
     */
    public byte[] fetchFile(final String sandboxName, final String filename) {
        byte[] content;
        try {
            content = this.sandboxExecutionRestClient.get()
                .uri(WORKSPACE_FILE_URI, sandboxName, this.namespace, EXEC_PORT, filename)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .body(byte[].class);
        } catch (IllegalArgumentException e) {
            throw invalidSandboxName(sandboxName, e);
        } catch (HttpClientErrorException.NotFound e) {
            throw new SandboxExecutionException(
                "There is no file named '" + filename + "' in the sandbox's working directory.",
                e);
        } catch (RestClientException e) {
            throw new SandboxExecutionException(
                "Could not read '" + filename + "' from sandbox '" + sandboxName + "': "
                    + describe(e), e);
        }
        return Objects.requireNonNullElse(content, new byte[0]);
    }

    /**
     * Checks whether a file is already staged in a sandbox's workspace, so a caller can skip
     * re-sending bytes that are already there -- and re-send them when they aren't, e.g. after
     * the sandbox's container restarted.
     *
     * @param sandboxName the exact sandbox name
     * @param filename the name the file would be staged under
     * @return {@code true} if the sandbox answered 2xx, {@code false} on any other status
     * @throws SandboxExecutionException if the sandbox is unreachable
     */
    public boolean isStaged(final String sandboxName, final String filename) {
        boolean staged;
        try {
            staged = Boolean.TRUE.equals(this.sandboxExecutionRestClient.head()
                .uri(WORKSPACE_FILE_URI, sandboxName, this.namespace, EXEC_PORT, filename)
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));
        } catch (IllegalArgumentException e) {
            throw invalidSandboxName(sandboxName, e);
        } catch (RestClientException e) {
            throw new SandboxExecutionException(
                "Could not check for '" + filename + "' in sandbox '" + sandboxName + "': "
                    + describe(e), e);
        }
        return staged;
    }

    /**
     * Checks whether the sandbox answers its health endpoint through its Service -- the same
     * route every real request takes. A passing readiness probe is not enough on its own: the
     * pod is reported ready before the Service's endpoints and kube-proxy's routing catch up, and
     * in that gap a connection to the Service is refused outright.
     *
     * @param sandboxName the exact sandbox name
     * @return {@code true} only if the sandbox answered 2xx; {@code false} for any other status
     *     or any failure to reach it at all
     */
    public boolean isReachable(final String sandboxName) {
        boolean reachable;
        try {
            reachable = Boolean.TRUE.equals(this.sandboxExecutionRestClient.get()
                .uri(HEALTH_URI, sandboxName, this.namespace, EXEC_PORT)
                .exchange((request, response) -> response.getStatusCode().is2xxSuccessful()));
        } catch (IllegalArgumentException | RestClientException e) {
            reachable = false;
        }
        return reachable;
    }

    /**
     * Describes a failed request by its most specific cause rather than the wrapper's own
     * message. The JDK HTTP client reports an unresolvable host -- e.g. a sandbox that does not
     * exist -- as a {@code ConnectException} with no message at all, which the wrapper would
     * otherwise render as a bare, useless {@code "null"}.
     */
    private static String describe(final RestClientException e) {
        var cause = NestedExceptionUtils.getMostSpecificCause(e);
        var message = cause.getMessage();
        return message == null
            ? cause.getClass().getSimpleName() + " (the sandbox may not exist or not be ready)"
            : message;
    }

    /**
     * Builds the error for a {@code sandboxName} that made the target URI unbuildable -- e.g. a
     * caller passing an entire code blob where a short sandbox name was expected. The message
     * deliberately omits the underlying {@link IllegalArgumentException}'s own message (kept only
     * as the cause): that message is the malformed URI itself, which would otherwise echo the
     * offending value's full (and potentially huge) content straight back to whatever called
     * this, unbounded.
     */
    private static SandboxExecutionException invalidSandboxName(
            final String sandboxName, final IllegalArgumentException cause) {
        return new SandboxExecutionException(
            "'" + preview(sandboxName) + "' is not a valid sandbox name -- expected the exact "
                + "name returned by create_sandbox or list_sandboxes.", cause);
    }

    private static String preview(final String value) {
        return value.length() > MAX_SANDBOX_NAME_IN_ERROR
            ? value.substring(0, MAX_SANDBOX_NAME_IN_ERROR) + "..."
            : value;
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
