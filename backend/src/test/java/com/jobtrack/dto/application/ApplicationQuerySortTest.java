package com.jobtrack.dto.application;

import com.jobtrack.dto.application.ApplicationQuery.Direction;
import com.jobtrack.dto.application.ApplicationQuery.SortField;
import com.jobtrack.dto.application.ApplicationQuery.SortKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for sort-key parsing.
 *
 * <p>Sort keys are whitelisted and mapped to entity attributes, so user input can never reach an
 * {@code ORDER BY} clause as raw text - the classic SQL injection point in "sortable table" features.
 * These tests pin that contract down.</p>
 */
class ApplicationQuerySortTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} {2}")
    @CsvSource({
            "newest, NEWEST, DESC",
            "oldest, OLDEST, ASC",
            "company, COMPANY, ASC",
            "title, TITLE, ASC",
            "status, STATUS, ASC",
            "applied_date, APPLIED_DATE, DESC",
            "applieddate, APPLIED_DATE, DESC",
            "APPLIED_DATE, APPLIED_DATE, DESC",
            "deadline, DEADLINE, ASC",
            "salary, SALARY, DESC",
            "updated, UPDATED, DESC",
    })
    void parsesWhitelistedKeysWithTheirDefaultDirection(String raw, SortField expectedField,
                                                        Direction expectedDirection) {
        SortKey key = SortField.from(raw);

        assertThat(key.field()).isEqualTo(expectedField);
        assertThat(key.direction()).isEqualTo(expectedDirection);
    }

    @Test
    @DisplayName("a leading minus flips the direction instead of being passed through")
    void minusPrefixReversesDirection() {
        assertThat(SortField.from("-deadline").direction()).isEqualTo(Direction.DESC);
        assertThat(SortField.from("deadline").direction()).isEqualTo(Direction.ASC);
        assertThat(SortField.from("-company").field()).isEqualTo(SortField.COMPANY);
        assertThat(SortField.from("-company").direction()).isEqualTo(Direction.DESC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankFallsBackToNewest(String raw) {
        SortKey key = SortField.from(raw);

        assertThat(key.field()).isEqualTo(SortField.NEWEST);
        assertThat(key.direction()).isEqualTo(Direction.DESC);
    }

    @Test
    void nullFallsBackToNewest() {
        assertThat(SortField.from(null).field()).isEqualTo(SortField.NEWEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password",
            "user_id",
            "createdAt; DROP TABLE users",
            "(SELECT 1)",
            "newest,",
    })
    @DisplayName("anything outside the whitelist is rejected, never interpolated")
    void rejectsUnknownKeys(String raw) {
        assertThatThrownBy(() -> SortField.from(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown sort key")
                .hasMessageContaining("NEWEST");
    }

    @Test
    void everyWhitelistedFieldMapsToAnEntityAttribute() {
        for (SortField field : SortField.values()) {
            assertThat(field.attribute()).isNotBlank();
            assertThat(field.name()).isIn(SortField.validKeys());
        }
    }

    @Test
    void directionOppositeIsSymmetric() {
        assertThat(Direction.ASC.opposite()).isEqualTo(Direction.DESC);
        assertThat(Direction.DESC.opposite()).isEqualTo(Direction.ASC);
    }
}
