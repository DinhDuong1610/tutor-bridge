package com.tutorbridge;

import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** One PostgreSQL per test JVM, provisioned by the same script as local development. */
public final class PostgresTestSupport {

    static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
    public static final String APP_PASSWORD = UUID.randomUUID().toString();

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withEnv("TB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("TB_APP_PASSWORD", APP_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/db-init/01-roles.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-roles.sh");

    static {
        POSTGRES.start();
    }

    private PostgresTestSupport() {}

    public static String jdbcUrl() {
        return POSTGRES.getJdbcUrl() + "&currentSchema=tutorbridge,public";
    }
}
