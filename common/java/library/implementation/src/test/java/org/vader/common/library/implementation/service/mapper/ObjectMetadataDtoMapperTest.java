package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

class ObjectMetadataDtoMapperTest {

    private final ObjectMetadataDtoMapper mapper = new ObjectMetadataDtoMapper();

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((ObjectMetadataEntity) null)).isNull();
    }

    @Test
    void map_copiesIdentityAuditAndScalarFields() {
        var createdAt = OffsetDateTime.parse("2026-01-02T03:04:05Z");
        var updatedAt = OffsetDateTime.parse("2026-02-03T04:05:06Z");

        var entity = new ObjectMetadataEntity();
        entity.setId("11111111-1111-1111-1111-111111111111");
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setBucketName("prompts");
        entity.setOriginalFilename("diagram.png");
        entity.setContentType("image/png");
        entity.setSize(2048L);

        var dto = this.mapper.map(entity);

        assertThat(dto.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
        assertThat(dto.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(dto.getBucketName()).isEqualTo("prompts");
        assertThat(dto.getOriginalFilename()).isEqualTo("diagram.png");
        assertThat(dto.getContentType()).isEqualTo("image/png");
        assertThat(dto.getSize()).isEqualTo(2048L);
        assertThat(dto.getModelType()).isEqualTo("ObjectMetadata");
    }

    @Test
    void map_list_mapsEachElementAndSkipsNothing() {
        var first = new ObjectMetadataEntity();
        first.setBucketName("a");
        var second = new ObjectMetadataEntity();
        second.setBucketName("b");

        var dtos = this.mapper.map(List.of(first, second));

        assertThat(dtos).extracting("bucketName").containsExactly("a", "b");
    }

    @Test
    void map_set_mapsEachElement() {
        var entity = new ObjectMetadataEntity();
        entity.setBucketName("a");

        assertThat(this.mapper.map(Set.of(entity))).hasSize(1);
    }

    @Test
    void map_nullCollection_returnsEmptyList() {
        assertThat(this.mapper.map((List<ObjectMetadataEntity>) null)).isEmpty();
        assertThat(this.mapper.map((Set<ObjectMetadataEntity>) null)).isEmpty();
    }
}
