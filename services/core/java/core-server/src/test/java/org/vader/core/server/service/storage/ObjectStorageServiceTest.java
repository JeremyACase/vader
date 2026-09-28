package org.vader.core.server.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.exceptions.ObjectNotFoundException;
import org.vader.core.server.repository.ObjectMetadataRepository;
import org.vader.core.server.service.strategies.storage.interfaces.InterfaceFileStorageStrategy;

class ObjectStorageServiceTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private ObjectMetadataRepository repository;
    private InterfaceFileStorageStrategy storageStrategy;
    private ObjectStorageService service;

    @BeforeEach
    void setUp() {
        this.repository = mock(ObjectMetadataRepository.class);
        this.storageStrategy = mock(InterfaceFileStorageStrategy.class);
        this.service = new ObjectStorageService();
        ReflectionTestUtils.setField(this.service, "repository", this.repository);
        ReflectionTestUtils.setField(this.service, "storageStrategy", this.storageStrategy);
    }

    private static ObjectMetadataEntity metadata() {
        var entity = new ObjectMetadataEntity();
        entity.setId(ID);
        entity.setOriginalFilename("diagram.png");
        entity.setContentType("image/png");
        entity.setSize(2048L);
        return entity;
    }

    @Test
    void describe_returnsTheDescriptiveFieldsWithoutTouchingTheStrategy() {
        when(this.repository.findById(ID)).thenReturn(Optional.of(metadata()));

        var descriptor = this.service.describe(ID);

        assertThat(descriptor.id()).isEqualTo(ID);
        assertThat(descriptor.filename()).isEqualTo("diagram.png");
        assertThat(descriptor.contentType()).isEqualTo("image/png");
        assertThat(descriptor.size()).isEqualTo(2048L);
        verifyNoInteractions(this.storageStrategy);
    }

    @Test
    void describe_withUnknownId_throwsObjectNotFoundException() {
        when(this.repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.describe(ID))
            .isInstanceOf(ObjectNotFoundException.class)
            .hasMessageContaining(ID);
    }

    @Test
    void retrieve_delegatesToTheActiveStrategyAndCarriesTheDescriptiveFields() {
        var metadata = metadata();
        var resource = new ByteArrayResource(new byte[] {1, 2, 3});
        when(this.repository.findById(ID)).thenReturn(Optional.of(metadata));
        when(this.storageStrategy.retrieve(metadata)).thenReturn(resource);

        var content = this.service.retrieve(ID);

        assertThat(content.resource()).isSameAs(resource);
        assertThat(content.filename()).isEqualTo("diagram.png");
        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.size()).isEqualTo(2048L);
    }

    @Test
    void retrieve_withUnknownId_throwsObjectNotFoundExceptionWithoutCallingTheStrategy() {
        when(this.repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.retrieve(ID))
            .isInstanceOf(ObjectNotFoundException.class);
        verify(this.storageStrategy, never()).retrieve(any());
    }
}
