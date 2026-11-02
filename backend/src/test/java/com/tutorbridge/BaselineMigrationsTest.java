package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CR-005: V1-V5 stay byte-identical to the approved appendix, V6 to the proposal from CR-009. */
class BaselineMigrationsTest {

    private static final Path REPO = Path.of("..");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @ParameterizedTest
    @CsvSource({
        "docs/appendix/g3_ky_thuat_v0_1/sql/001_foundation.sql, V1__foundation.sql",
        "docs/appendix/g3_ky_thuat_v0_1/sql/002_integrity_guards.sql, V2__integrity_guards.sql",
        "docs/appendix/g3_ky_thuat_v0_1/sql/003_review_corrections.sql, V3__review_corrections.sql",
        "docs/appendix/g3_ky_thuat_v0_1/sql/004_f03_f05_candidate.sql, V4__f03_f05_candidate.sql",
        "docs/appendix/g3_ky_thuat_v0_1/sql/005_identity_support_privacy_candidate.sql, V5__identity_support_privacy_candidate.sql",
        "docs/design/migrations-proposed/006_scale_readiness.sql, V6__scale_readiness.sql",
    })
    void migration_matches_its_approved_source(String source, String migration) throws IOException {
        assertThat(Files.mismatch(REPO.resolve(source), MIGRATIONS.resolve(migration))).isEqualTo(-1L);
    }
}
