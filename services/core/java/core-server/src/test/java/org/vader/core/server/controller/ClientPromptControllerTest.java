package org.vader.core.server.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.vader.common.model.vader.dto.ClientPrompt;

class ClientPromptControllerTest {

    private final ClientPromptController controller = new ClientPromptController();

    @Test
    void receivePrompt_withTextOnly_returnsOk() {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        ResponseEntity<Void> response = this.controller.receivePrompt(clientPrompt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void receivePrompt_withTextAndFiles_returnsOk() {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("Summarize the attached file.");
        clientPrompt.setFiles(List.of(new MockMultipartFile(
            "files",
            "notes.txt",
            "text/plain",
            "some content".getBytes())));

        ResponseEntity<Void> response = this.controller.receivePrompt(clientPrompt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
