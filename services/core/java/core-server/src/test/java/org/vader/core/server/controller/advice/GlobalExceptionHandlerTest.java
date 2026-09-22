package org.vader.core.server.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.vader.core.server.exceptions.AssignmentAlreadyTerminalException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.exceptions.UnknownAssignmentException;
import org.vader.core.server.exceptions.UnknownToolException;
import org.vader.core.server.service.strategies.storage.FileStorageException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        this.request = mock(HttpServletRequest.class);
        when(this.request.getRequestURI()).thenReturn("/vader/core-server/agent/tool-calls");
    }

    @Test
    void handleUnknownAssignment_returnsNotFoundWithStableErrorCode() {
        var response = this.handler.handleUnknownAssignment(
            new UnknownAssignmentException("Unknown assignment: abc"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().error()).isEqualTo("unknown_assignment");
        assertThat(response.getBody().message()).contains("abc");
    }

    @Test
    void handleAlreadyTerminal_returnsConflictWithStableErrorCode() {
        var response = this.handler.handleAlreadyTerminal(
            new AssignmentAlreadyTerminalException("already SUCCEEDED"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("assignment_already_terminal");
    }

    @Test
    void handleInferenceUnavailable_returnsBadGatewayWithStableErrorCode() {
        var response = this.handler.handleInferenceUnavailable(
            new OrchestratorUnavailableException("connection refused", new IOException()),
            this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error()).isEqualTo("inference_unavailable");
    }

    @Test
    void handleUnknownTool_returnsBadRequestWithStableErrorCode() {
        var response = this.handler.handleUnknownTool(
            new UnknownToolException("No tool registered with name 'bogus_tool'"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("unknown_tool");
        assertThat(response.getBody().message()).contains("bogus_tool");
    }

    @Test
    void handleKubernetes_returnsBadGatewayWithStableErrorCode() {
        var response = this.handler.handleKubernetes(
            new KubernetesClientException("api server unreachable"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error()).isEqualTo("kubernetes_error");
    }

    @Test
    void handleExecutionFailure_returnsBadGatewayWithStableErrorCode() {
        var response = this.handler.handleExecutionFailure(
            new SandboxExecutionException("connection refused", null), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error()).isEqualTo("sandbox_execution_failed");
    }

    @Test
    void handleFileStorage_returnsInternalServerErrorWithStableErrorCode() {
        var response = this.handler.handleFileStorage(
            new FileStorageException("disk full", new IOException("no space")), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("file_storage_failed");
        assertThat(response.getBody().message()).contains("disk full");
    }

    @Test
    void handleBindException_returnsBadRequestJoiningEveryViolation() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "clientPrompt");
        bindingResult.reject("too_many_files", "No more than 5 files may be attached");
        var exception = new BindException(bindingResult);

        var response = this.handler.handleBindException(exception, this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("validation_failed");
        assertThat(response.getBody().message()).contains("No more than 5 files");
    }

    @Test
    void handleMalformedRequest_returnsBadRequestInsteadOfInternalServerError() {
        var response = this.handler.handleMalformedRequest(
            new HttpMessageNotReadableException("Unexpected token"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("malformed_request");
    }

    @Test
    void handleIllegalArgument_returnsBadRequestWithGenericErrorCode() {
        var response = this.handler.handleIllegalArgument(
            new IllegalArgumentException("no queue for 'Bogus'"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error()).isEqualTo("invalid_argument");
        assertThat(response.getBody().message()).contains("Bogus");
    }

    @Test
    void handleUnexpected_returnsInternalServerErrorInsteadOfPropagating() {
        var response = this.handler.handleUnexpected(
            new IllegalStateException("something nobody anticipated"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("internal_error");
        assertThat(response.getBody().message()).contains("nobody anticipated");
    }
}
