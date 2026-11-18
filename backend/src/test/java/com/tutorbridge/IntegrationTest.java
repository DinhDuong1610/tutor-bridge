package com.tutorbridge;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Migrates as tutorbridge_migrator and runs as tutorbridge_app, as deployed. */
@SpringBootTest
public abstract class IntegrationTest {

    @Autowired
    protected JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestSupport::jdbcUrl);
        registry.add("spring.datasource.password", () -> PostgresTestSupport.APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.password", () -> PostgresTestSupport.MIGRATOR_PASSWORD);
        identityProperties().forEach((name, value) -> registry.add(name, () -> value));
    }

    /** Settings the identity module needs to start; also for a second instance built by hand. */
    protected static java.util.Map<String, String> identityProperties() {
        return java.util.Map.of(
                "tutorbridge.identity.keys.email-digest", TEST_KEYS[0],
                "tutorbridge.identity.keys.email-encryption", TEST_KEYS[1],
                "tutorbridge.identity.keys.link-token", TEST_KEYS[2],
                "tutorbridge.identity.terms-versions", TERMS_VERSION,
                "tutorbridge.identity.evidence.dir", EVIDENCE_DIR.toString());
    }

    /** Root of the local ObjectStore in tests: a fresh temporary directory per test run. */
    protected static java.nio.file.Path evidenceDir() {
        return EVIDENCE_DIR;
    }

    private static final java.nio.file.Path EVIDENCE_DIR;

    static {
        try {
            EVIDENCE_DIR = java.nio.file.Files.createTempDirectory("tutorbridge-evidence-");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The only terms version tests may register with. */
    protected static final String TERMS_VERSION = "test-terms-1";

    /** Fresh random keys per test run, so no key is ever written into the repository. */
    private static final String[] TEST_KEYS = java.util.stream.Stream.generate(() -> {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }).limit(3).toArray(String[]::new);

    protected UUID newAccount() {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, 'PARENT', 'ACTIVE')").param(id).update();
        return id;
    }
}
