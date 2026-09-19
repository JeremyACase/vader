package org.vader.core.server.service.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.models.SandboxExecutionRequest;

class SandboxExecutionClientTest {

    private MockRestServiceServer mockServer;
    private SandboxExecutionClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        this.client = new SandboxExecutionClient();
        ReflectionTestUtils.setField(this.client, "sandboxExecutionRestClient", builder.build());
        ReflectionTestUtils.setField(this.client, "namespace", "vader");
    }

    @Test
    void execute_postsToTheSandboxsServiceAndReturnsTheResult() {
        this.mockServer
            .expect(requestTo("http://vader-sandbox-a.vader.svc.cluster.local:8888/execute"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(
                "{\"code\":\"print('hi')\",\"files\":{},\"timeoutSeconds\":null}"))
            .andRespond(withSuccess(
                "{\"stdout\":\"hi\\n\",\"stderr\":\"\",\"exitCode\":0,\"timedOut\":false}",
                MediaType.APPLICATION_JSON));

        var result = this.client.execute(
            "vader-sandbox-a", new SandboxExecutionRequest("print('hi')", Map.of(), null));

        assertThat(result.stdout()).isEqualTo("hi\n");
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.timedOut()).isFalse();
        this.mockServer.verify();
    }

    @Test
    void execute_withNullFiles_sendsAnEmptyMapInstead() {
        this.mockServer
            .expect(requestTo("http://vader-sandbox-a.vader.svc.cluster.local:8888/execute"))
            .andExpect(content().json(
                "{\"code\":\"print(1)\",\"files\":{},\"timeoutSeconds\":null}"))
            .andRespond(withSuccess(
                "{\"stdout\":\"1\\n\",\"stderr\":\"\",\"exitCode\":0,\"timedOut\":false}",
                MediaType.APPLICATION_JSON));

        this.client.execute("vader-sandbox-a", new SandboxExecutionRequest("print(1)", null, null));

        this.mockServer.verify();
    }

    @Test
    void execute_whenTheSandboxIsUnreachable_throwsSandboxExecutionException() {
        this.mockServer
            .expect(requestTo("http://vader-sandbox-a.vader.svc.cluster.local:8888/execute"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> this.client.execute(
            "vader-sandbox-a", new SandboxExecutionRequest("pass", Map.of(), null)))
            .isInstanceOf(SandboxExecutionException.class)
            .hasMessageContaining("vader-sandbox-a");
    }

    @Test
    void stageFile_putsTheRawBytesToTheSandboxsWorkspaceEndpoint() {
        this.mockServer
            .expect(requestTo(
                "http://vader-sandbox-a.vader.svc.cluster.local:8888/workspace/files/report.xlsx"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(content().bytes("raw bytes".getBytes()))
            .andRespond(withSuccess("{\"filename\":\"report.xlsx\",\"size\":9}",
                MediaType.APPLICATION_JSON));

        this.client.stageFile("vader-sandbox-a", "report.xlsx", "raw bytes".getBytes());

        this.mockServer.verify();
    }

    @Test
    void stageFile_whenTheSandboxIsUnreachable_throwsSandboxExecutionException() {
        this.mockServer
            .expect(requestTo(
                "http://vader-sandbox-a.vader.svc.cluster.local:8888/workspace/files/report.xlsx"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> this.client.stageFile(
            "vader-sandbox-a", "report.xlsx", "raw bytes".getBytes()))
            .isInstanceOf(SandboxExecutionException.class)
            .hasMessageContaining("report.xlsx")
            .hasMessageContaining("vader-sandbox-a");
    }
}
