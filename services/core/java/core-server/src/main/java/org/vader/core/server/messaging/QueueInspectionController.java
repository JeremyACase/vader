package org.vader.core.server.messaging;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.vader.common.model.vader.dto.AbstractOutboxMessage;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.messaging.model.QueueMessagePage;
import org.vader.core.server.messaging.model.QueueSummary;

/**
 * Read-only inspection of the inbox/outbox queues and their individual messages, for operators.
 * Deliberately a plain REST controller rather than a DAO controller, so queue messages -- LLM
 * prompts included -- are never published as MCP tools.
 */
@RestController
@RequestMapping("/vader/core-server/queue")
public class QueueInspectionController {

    @Autowired
    private QueueInspectionRegistry registry;

    /**
     * Summarizes every queue.
     *
     * @return one summary per queue
     */
    @GetMapping
    public List<QueueSummary> queues() {
        return this.registry.all().stream()
            .map(AbstractQueueInspectionAdapter::summary)
            .toList();
    }

    /**
     * Lists one page of a queue's messages, newest first.
     *
     * @param queue the queue name, e.g. {@code "LlmRequest"}
     * @param page the zero-based page number
     * @param size the page size
     * @param status only messages in this status, if given
     * @return the page
     * @throws IllegalArgumentException if the queue or status is unknown, or the page is out of
     *     range
     */
    @GetMapping("/{queue}/message")
    public QueueMessagePage messages(
        @PathVariable("queue") final String queue,
        @RequestParam("page") final int page,
        @RequestParam("size") final int size,
        @RequestParam(value = "status", required = false) final String status) {

        var matching = Optional.ofNullable(status).map(OutboxMessageStatus::valueOf).orElse(null);
        return this.registry.require(queue).messages(matching, page, size);
    }

    /**
     * Fetches one message in full, payload included.
     *
     * @param queue the queue name
     * @param id the message id
     * @return the message (200), or an empty 204 if the queue has none with that id
     * @throws IllegalArgumentException if the queue is unknown
     */
    @GetMapping("/{queue}/message/{id}")
    public ResponseEntity<AbstractOutboxMessage> message(
        @PathVariable("queue") final String queue,
        @PathVariable("id") final String id) {

        return this.registry.require(queue).message(id)
            .<ResponseEntity<AbstractOutboxMessage>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }
}
