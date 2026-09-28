package org.vader.core.server.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.service.ClientPromptIntakeService;

/**
 * Accepts client-submitted prompts. The prompt is persisted and queued for decomposition; the
 * response is a {@link IngressResponse} receipt, not the finished workflow. Callers poll the
 * workflow query API by {@code clientPromptId} for the eventual decomposition, and the
 * backpressure endpoint for how backed up the queue is.
 */
@RestController
public class ClientPromptController {

    private static final Logger logger = LoggerFactory.getLogger(ClientPromptController.class);

    @Autowired
    private ClientPromptIntakeService clientPromptIntakeService;

    /**
     * Accepts a client prompt for asynchronous decomposition.
     *
     * @param clientPrompt the submitted prompt text and any attached files
     * @return 202 with a receipt identifying the persisted prompt
     */
    @PostMapping("/vader/core-server/client-prompt")
    public ResponseEntity<IngressResponse> receivePrompt(
        @Valid @ModelAttribute final ClientPrompt clientPrompt) {

        logger.info(
            "Received client prompt: text='{}', fileCount={}",
            clientPrompt.getText(),
            clientPrompt.getFiles().size());

        var ingressResponse = this.clientPromptIntakeService.accept(clientPrompt);
        return ResponseEntity.accepted().body(ingressResponse);
    }
}
