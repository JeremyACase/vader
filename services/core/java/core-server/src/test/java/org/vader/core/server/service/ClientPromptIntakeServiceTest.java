package org.vader.core.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoToEntityMapper;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.service.io.ClientPromptOutbox;
import org.vader.core.server.service.strategies.storage.interfaces.InterfaceFileStorageStrategy;

class ClientPromptIntakeServiceTest {

    private InterfaceFileStorageStrategy fileStorageStrategy;
    private ClientPromptRepository clientPromptRepository;
    private ClientPromptOutbox clientPromptOutbox;
    private ClientPromptIntakeService service;

    @BeforeEach
    void setUp() {
        this.fileStorageStrategy = mock(InterfaceFileStorageStrategy.class);
        this.clientPromptRepository = mock(ClientPromptRepository.class);
        this.clientPromptOutbox = mock(ClientPromptOutbox.class);
        this.service = new ClientPromptIntakeService();
        ReflectionTestUtils.setField(
            this.service, "clientPromptDtoToEntityMapper", new ClientPromptDtoToEntityMapper());
        ReflectionTestUtils.setField(
            this.service, "fileStorageStrategy", this.fileStorageStrategy);
        ReflectionTestUtils.setField(
            this.service, "clientPromptRepository", this.clientPromptRepository);
        ReflectionTestUtils.setField(this.service, "clientPromptOutbox", this.clientPromptOutbox);

        when(this.clientPromptRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(this.clientPromptOutbox.enqueue(any())).thenReturn(new IngressResponse());
    }

    private static ClientPrompt dto(final String text) {
        var prompt = new ClientPrompt();
        prompt.setText(text);
        return prompt;
    }

    @Test
    void accept_persistsThePromptThenEnqueuesIt() {
        var receipt = this.service.accept(dto("decompose this"));

        var saved = ArgumentCaptor.forClass(ClientPromptEntity.class);
        verify(this.clientPromptRepository).save(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo("decompose this");
        verify(this.clientPromptOutbox).enqueue(saved.getValue());
        assertThat(receipt).isNotNull();
    }

    @Test
    void accept_withNoAttachments_doesNotTouchFileStorage() {
        this.service.accept(dto("no files here"));

        verify(this.fileStorageStrategy, never()).store(any());
    }

    @Test
    void accept_withAttachments_storesThemAndLinksThemToThePrompt() {
        var dto = dto("summarize the file");
        dto.setFiles(List.of(new MockMultipartFile(
            "files", "notes.txt", "text/plain", "content".getBytes())));
        var stored = new ObjectMetadataEntity();
        when(this.fileStorageStrategy.store(any())).thenReturn(List.of(stored));

        this.service.accept(dto);

        verify(this.fileStorageStrategy).store(dto.getFiles());
        var saved = ArgumentCaptor.forClass(ClientPromptEntity.class);
        verify(this.clientPromptRepository).save(saved.capture());
        assertThat(saved.getValue().getFiles()).containsExactly(stored);
        assertThat(stored.getClientPrompt()).isSameAs(saved.getValue());
    }
}
