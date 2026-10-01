package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.dao.service.PageValidator;
import org.vader.common.library.implementation.service.mapper.LlmRequestOutboxMessageDtoMapper;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/** Covers the shared inspection behaviour through the LLM request queue's adapter. */
class LlmRequestQueueInspectionAdapterTest {

    private final LlmRequestInbox inbox = mock(LlmRequestInbox.class);

    private final LlmRequestOutboxMessageRepository repository =
        mock(LlmRequestOutboxMessageRepository.class);

    private LlmRequestQueueInspectionAdapter adapter;

    @BeforeEach
    void setUp() {
        var pageValidator = new PageValidator();
        ReflectionTestUtils.setField(pageValidator, "maxPageSize", 100);
        this.adapter = new LlmRequestQueueInspectionAdapter();
        ReflectionTestUtils.setField(this.adapter, "inbox", this.inbox);
        ReflectionTestUtils.setField(this.adapter, "repository", this.repository);
        ReflectionTestUtils.setField(
            this.adapter, "mapper", new LlmRequestOutboxMessageDtoMapper());
        ReflectionTestUtils.setField(this.adapter, "pageValidator", pageValidator);
        when(this.inbox.queuedModelType()).thenReturn("LlmRequest");
        when(this.inbox.maxOpenMessages()).thenReturn(1);
    }

    @Test
    void queueName_isTheInboxQueuedModelType() {
        assertThat(this.adapter.queueName()).isEqualTo("LlmRequest");
    }

    @Test
    void summary_countsEveryStatus() {
        when(this.repository.countByStatus(OutboxMessageStatus.PENDING)).thenReturn(3L);
        when(this.repository.countByStatus(OutboxMessageStatus.CLAIMED)).thenReturn(1L);
        when(this.repository.countByStatus(OutboxMessageStatus.PROCESSED)).thenReturn(40L);
        when(this.repository.countByStatus(OutboxMessageStatus.FAILED)).thenReturn(2L);

        var summary = this.adapter.summary();

        assertThat(summary.name()).isEqualTo("LlmRequest");
        assertThat(summary.maxOpenMessages()).isEqualTo(1);
        assertThat(summary.statusCounts()).containsExactly(
            entry(OutboxMessageStatus.PENDING, 3L),
            entry(OutboxMessageStatus.CLAIMED, 1L),
            entry(OutboxMessageStatus.PROCESSED, 40L),
            entry(OutboxMessageStatus.FAILED, 2L));
    }

    @Test
    void messages_withoutStatus_pagesEveryMessageNewestFirst() {
        var entity = Instancio.create(LlmRequestOutboxMessageEntity.class);
        var expectedPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(this.repository.findAll(expectedPageable))
            .thenReturn(new PageImpl<>(List.of(entity), expectedPageable, 11));

        var page = this.adapter.messages(null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(11);
        assertThat(page.totalPages()).isEqualTo(2);
        var row = page.content().get(0);
        assertThat(row.id()).isEqualTo(entity.getId());
        assertThat(row.status()).isEqualTo(entity.getStatus());
        assertThat(row.attempts()).isEqualTo(entity.getAttempts());
        assertThat(row.subject()).isEqualTo(entity.getKind().name());
        verify(this.repository, never()).findByStatus(any(), any());
    }

    @Test
    void messages_withStatus_pagesOnlyThatStatus() {
        var entity = Instancio.create(LlmRequestOutboxMessageEntity.class);
        entity.setStatus(OutboxMessageStatus.FAILED);
        when(this.repository.findByStatus(eq(OutboxMessageStatus.FAILED), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(entity)));

        var page = this.adapter.messages(OutboxMessageStatus.FAILED, 0, 10);

        assertThat(page.content()).extracting(row -> row.status())
            .containsExactly(OutboxMessageStatus.FAILED);
        verify(this.repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void messages_overTheMaxPageSize_isRejected() {
        assertThatThrownBy(() -> this.adapter.messages(null, 0, 101))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void message_returnsTheFullMessageIncludingPayload() {
        var entity = Instancio.create(LlmRequestOutboxMessageEntity.class);
        when(this.repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        var message = this.adapter.message(entity.getId()).orElseThrow();

        assertThat(message.getRequestJson()).isEqualTo(entity.getRequestJson());
        assertThat(message.getResponseJson()).isEqualTo(entity.getResponseJson());
    }

    @Test
    void message_whenAbsent_isEmpty() {
        when(this.repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(this.adapter.message("missing")).isEmpty();
    }
}
