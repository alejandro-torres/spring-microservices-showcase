package com.showcase.common.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditedModelTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T09:00:00Z");

    private static final class Sample extends AuditedModel {
        Sample(Long version) {
            super(7L, true, version, CREATED_AT, CREATED_AT);
        }
    }

    @Test
    @DisplayName("checkVersion acepta la versión actual y rechaza otra con CONCURRENT_MODIFICATION")
    void checkVersion_shouldDetectConcurrentChanges() {
        var model = new Sample(3L);

        model.checkVersion(3L);

        assertThatThrownBy(() -> model.checkVersion(2L))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
        assertThatThrownBy(() -> model.checkVersion(null)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("deactivate hace el borrado lógico y conserva el resto de datos")
    void deactivate_shouldOnlyChangeActive() {
        var model = new Sample(0L);

        model.deactivate();

        assertThat(model.isActive()).isFalse();
        assertThat(model.getId()).isEqualTo(7L);
        assertThat(model.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(model.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(model.getVersion()).isZero();
    }
}
