package com.jobtrack.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for REST integration tests.
 *
 * <p>Database strategy, in order of preference:</p>
 * <ol>
 *   <li><b>Testcontainers PostgreSQL</b> when a Docker daemon is reachable - the same image used in
 *       production, so the tests exercise real PostgreSQL behaviour (CHECK constraints, partial
 *       indexes, TIMESTAMPTZ).</li>
 *   <li><b>An existing PostgreSQL instance</b> described by {@code TEST_DB_URL} /
 *       {@code TEST_DB_USERNAME} / {@code TEST_DB_PASSWORD}, defaulting to
 *       {@code jdbc:postgresql://localhost:5432/jobtrack_test}. Used on machines without Docker.</li>
 * </ol>
 *
 * <p>H2 is deliberately not an option: the migration is PostgreSQL-specific and testing against a
 * different dialect would prove nothing about the real schema.</p>
 *
 * <p>Each test runs in a transaction that is rolled back, so tests never see each other's rows and
 * the database can be reused across runs.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {

    private static final String FALLBACK_URL = "jdbc:postgresql://localhost:5432/jobtrack_test";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** Started once per JVM; {@code null} when Docker is unavailable. */
    private static final PostgresSupport POSTGRES = PostgresSupport.initialise();

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES.container() != null) {
            registry.add("spring.datasource.url", POSTGRES.container()::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES.container()::getUsername);
            registry.add("spring.datasource.password", POSTGRES.container()::getPassword);
        } else {
            registry.add("spring.datasource.url",
                    () -> System.getenv().getOrDefault("TEST_DB_URL", FALLBACK_URL));
            registry.add("spring.datasource.username",
                    () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "jobtrack"));
            registry.add("spring.datasource.password",
                    () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "jobtrack"));
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    /** Unique per test method so a leaked row can never satisfy another test's lookup. */
    protected String email;

    @BeforeEach
    void resetEmail() {
        this.email = uniqueEmail();
    }

    // ------------------------------------------------------------------ helpers

    protected static String uniqueEmail() {
        return "user" + SEQUENCE.incrementAndGet() + "." + System.nanoTime() + "@jobtrack.test";
    }

    /** Registers through the real API and returns the full auth response. */
    protected JsonNode register(String email, String password, String name) throws Exception {
        String body = mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("name", name, "email", email, "password", password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    protected JsonNode register() throws Exception {
        return register(email, "Password123", "Test Candidate");
    }

    protected String accessToken(String email, String password) throws Exception {
        return register(email, password, "Test Candidate").path("accessToken").asText();
    }

    /** Registers a user and returns just the access token. */
    protected String registerAndReturnToken() throws Exception {
        return register().path("accessToken").asText();
    }

    protected String registerSecondUser() throws Exception {
        return accessToken(uniqueEmail(), "Password123");
    }

    /** Promotes an existing account to ADMIN directly in the database. */
    protected void promoteToAdmin(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("No such user: " + email));
        user.setRole(Role.ADMIN);
        userRepository.saveAndFlush(user);
    }

    protected String registerAdmin() throws Exception {
        String adminEmail = uniqueEmail();
        JsonNode auth = register(adminEmail, "Password123", "Ada Admin");
        promoteToAdmin(adminEmail);
        // The role is read from the database on every request, so the existing token now carries it.
        return auth.path("accessToken").asText();
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body)
            throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected JsonNode readJson(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    /** True when a Docker daemon is reachable, checked cheaply before touching Testcontainers. */
    static boolean dockerLooksAvailable() {
        if (System.getenv("DOCKER_HOST") != null) {
            return true;
        }
        return Files.exists(Path.of("/var/run/docker.sock"));
    }
}
