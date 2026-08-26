package org.vader.core.server.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.model.vader.dto.ClientPrompt;

/** Accepts client-submitted prompts. Persistence is not yet wired up. */
@RestController
public class ClientPromptController {

    private static final Logger logger = LoggerFactory.getLogger(ClientPromptController.class);

    /**
     * Logs an incoming client prompt.
     *
     * @param clientPrompt the submitted prompt text and any attached files
     * @return an empty 200 OK response
     */
    @PostMapping("/vader/core-server/client-prompt")
    public ResponseEntity<Void> receivePrompt(@Valid @ModelAttribute ClientPrompt clientPrompt) {
        logger.info(
            "Received client prompt: text='{}', fileCount={}",
            clientPrompt.getText(),
            clientPrompt.getFiles().size());
        return ResponseEntity.ok().build();
    }
}
