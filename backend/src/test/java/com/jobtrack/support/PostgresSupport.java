package com.jobtrack.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Decides, once per JVM, where integration tests get their PostgreSQL from.
 *
 * <p>Testcontainers is preferred. When Docker is not available (a sandboxed CI runner, a machine
 * without the daemon) the tests fall back to a locally running PostgreSQL instead of silently
 * skipping - a skipped security test is worse than no test, because it looks green.</p>
 */
record PostgresSupport(PostgreSQLContainer<?> container, String mode) {

    private static final Logger log = LoggerFactory.getLogger(PostgresSupport.class);
    private static final String IMAGE = "postgres:17-alpine";

    static PostgresSupport initialise() {
        if (!IntegrationTestSupport.dockerLooksAvailable()) {
            log.info("Docker socket not found - integration tests will use TEST_DB_URL or {}",
                    "jdbc:postgresql://localhost:5432/jobtrack_test");
            return new PostgresSupport(null, "local-postgres");
        }
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                log.info("Docker daemon unreachable - falling back to a local PostgreSQL");
                return new PostgresSupport(null, "local-postgres");
            }
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("jobtrack_test")
                    .withUsername("jobtrack")
                    .withPassword("jobtrack");
            postgres.start();
            Runtime.getRuntime().addShutdownHook(new Thread(postgres::stop));
            log.info("Started Testcontainer {} for integration tests", IMAGE);
            return new PostgresSupport(postgres, "testcontainers");
        } catch (RuntimeException | Error failure) {
            log.warn("Could not start Testcontainers ({}); falling back to a local PostgreSQL",
                    failure.toString());
            return new PostgresSupport(null, "local-postgres");
        }
    }
}
