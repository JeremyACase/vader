package org.vader.core.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.service.ClientPromptIntakeService;
import org.vader.core.server.storage.FileStorageException;

class ClientPromptControllerTest {

    private ClientPromptIntakeService intakeService;
    private ClientPromptController controller;

    @BeforeEach
    void setUp() {
        this.intakeService = mock(ClientPromptIntakeService.class);
        this.controller = new ClientPromptController();
        ReflectionTestUtils.setField(
            this.controller, "clientPromptIntakeService", this.intakeService);
    }

    private static ClientPrompt prompt(final String text) {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText(text);
        return clientPrompt;
    }

    private static IngressResponse receipt() {
        var response = new IngressResponse();
        response.setId("11111111-1111-1111-1111-111111111111");
        response.setPayloadModelType("ClientPrompt");
        return response;
    }

    @Test
    void receivePrompt_acceptsThePromptAndReturnsReceipt() {
        var receipt = receipt();
        when(this.intakeService.accept(any())).thenReturn(receipt);

        var response = this.controller.receivePrompt(prompt("What's the weather like?"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isSameAs(receipt);
        assertThat(response.getBody().getPayloadModelType()).isEqualTo("ClientPrompt");
    }

    @Test
    void receivePrompt_withAttachments_acceptsThePrompt() {
        var clientPrompt = prompt("Summarize the attached file.");
        clientPrompt.setFiles(List.of(new MockMultipartFile(
            "files", "notes.txt", "text/plain", "some content".getBytes())));
        when(this.intakeService.accept(any())).thenReturn(receipt());

        var response = this.controller.receivePrompt(clientPrompt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void handleFileStorage_returnsInternalServerErrorWithErrorBody() {
        var response = this.controller.handleFileStorage(
            new FileStorageException("disk full", new java.io.IOException("no space")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("file_storage_failed");
        assertThat(response.getBody().message()).contains("disk full");
    }
}
