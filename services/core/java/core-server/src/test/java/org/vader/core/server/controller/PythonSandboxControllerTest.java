package org.vader.core.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.service.operators.pythonsandbox.PythonSandboxService;

@WebMvcTest(
    controllers = PythonSandboxController.class,
    properties = "vader.operators.python-sandbox.enabled=true")
class PythonSandboxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PythonSandboxService service;

    @Test
    void create_returnsTheSandbox() throws Exception {
        when(this.service.create(any())).thenReturn(
            new SandboxInfo("vader-sandbox-a", "vader", "Pending", "addr"));

        this.mockMvc.perform(post("/vader/core-server/python-sandbox/sandboxes")
                .contentType("application/json")
                .content("{\"name\":\"a\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("vader-sandbox-a"))
            .andExpect(jsonPath("$.phase").value("Pending"));
    }

    @Test
    void list_returnsEverySandbox() throws Exception {
        when(this.service.list()).thenReturn(List.of(
            new SandboxInfo("vader-sandbox-a", "vader", "Running", "addr")));

        this.mockMvc.perform(get("/vader/core-server/python-sandbox/sandboxes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("vader-sandbox-a"));
    }

    @Test
    void delete_returns204() throws Exception {
        this.mockMvc.perform(delete("/vader/core-server/python-sandbox/sandboxes/vader-sandbox-a"))
            .andExpect(status().isNoContent());
    }

    @Test
    void whenKubernetesFails_returns502() throws Exception {
        doThrow(new KubernetesClientException("api server unreachable"))
            .when(this.service).list();

        this.mockMvc.perform(get("/vader/core-server/python-sandbox/sandboxes"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("kubernetes_error"));
    }
}
