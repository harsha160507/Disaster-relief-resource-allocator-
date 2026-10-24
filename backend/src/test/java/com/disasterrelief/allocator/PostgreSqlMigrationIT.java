package com.disasterrelief.allocator;

import static org.junit.jupiter.api.Assertions.*;

import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.OrganizationRepository;
import com.disasterrelief.allocator.repository.UserProfileRepository;
import com.disasterrelief.allocator.service.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Opt-in real PostgreSQL checks: mvn -Ppostgres-it verify.
 * Each test owns a random schema; no existing application schema is altered.
 */
class PostgreSqlMigrationIT {
    private String url;
    private String user;
    private String password;
    private String schema;

    @BeforeEach
    void createDisposableSchema() throws Exception {
        url = required("PG_TEST_URL");
        user = required("PG_TEST_USERNAME");
        password = required("PG_TEST_PASSWORD");
        assertTrue(url.startsWith("jdbc:postgresql:"), "PG_TEST_URL must select PostgreSQL");
        schema = "phase1_it_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
    }

    @AfterEach
    void removeOnlyOwnedSchema() throws Exception {
        if (schema != null && schema.matches("phase1_it_[a-f0-9]{32}")) {
            execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void freshDatabaseMigratesValidatesRunsWorkflowAndSurvivesRestart() throws Exception {
        UUID organizationId;
        try (var context = startApplication()) {
            Flyway flyway = context.getBean(Flyway.class);
            assertEquals(4, flyway.info().applied().length);
            assertEquals(0, flyway.info().pending().length);
            assertEquals(14, scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema = '" + schema + "'"));
            var organization = new Organization();
            organization.setName("PostgreSQL migration test");
            organization = context.getBean(OrganizationRepository.class).save(organization);
            organizationId = organization.getId();
            var requester = new UserProfile();
            requester.setExternalSubject("postgres-it");
            requester.setDisplayName("PostgreSQL test agent");
            requester.setOrganization(organization);
            requester = context.getBean(UserProfileRepository.class).save(requester);

            var locations = context.getBean(LocationService.class);
            var disasters = context.getBean(DisasterService.class);
            var resources = context.getBean(ResourceService.class);
            var requests = context.getBean(RequestService.class);
            var allocations = context.getBean(AllocationService.class);
            var location = locations.create("Test shelter", "Test address", organizationId, null, null);
            var disaster = disasters.create("Test flood", DisasterType.FLOOD, null, Instant.now());
            disasters.activate(disaster.getId());
            disasters.addAffectedLocation(disaster.getId(), location.getId());
            var resource = resources.createResourceType("Test water", "litres", false);
            var inventory = resources.receive(location.getId(), resource.getId(), new BigDecimal("50"));
            var request = requests.create("POSTGRES-IT", disaster.getId(), requester.getId(),
                    location.getId(), RequestPriority.HIGH, null);
            requests.addItem(request.getId(), resource.getId(), new BigDecimal("20"));
            requests.submit(request.getId());
            requests.approve(request.getId());
            var result = allocations.allocate(request.getId());
            assertEquals(1, result.size());
            assertEquals(0, result.getFirst().getQuantity().compareTo(new BigDecimal("20")));
            assertEquals(RequestStatus.FULFILLED, requests.find(request.getId()).getStatus());
            assertEquals(0, resources.findInventory(inventory.getId()).getReservedQuantity()
                    .compareTo(new BigDecimal("20")));
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/requests")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        try (var restarted = startApplication()) {
            assertTrue(restarted.getBean(OrganizationRepository.class).findById(organizationId).isPresent());
            assertEquals(4, restarted.getBean(Flyway.class).info().applied().length);
            assertEquals(0, restarted.getBean(Flyway.class).migrate().migrationsExecuted);
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".allocations"));
        }
    }

    @Test
    void databaseRejectsInvalidQuantitiesDuplicateStockAndBrokenReferences() throws Exception {
        flyway().migrate();
        UUID organization = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        execute("INSERT INTO " + schema + ".organizations VALUES ('" + organization + "', now(), now(), 'Test', NULL, NULL, true)");
        execute("INSERT INTO " + schema + ".locations (id, created_at, updated_at, name, address, organization_id) VALUES ('"
                + location + "', now(), now(), 'Test', 'Test', '" + organization + "')");
        execute("INSERT INTO " + schema + ".resource_types VALUES ('" + resource + "', now(), now(), 'Water', 'litres', false, true)");
        String stock = "INSERT INTO " + schema
                + ".inventory (id, created_at, updated_at, location_id, resource_type_id, quantity, reserved_quantity, version) VALUES ('";
        String references = "', now(), now(), '" + location + "', '" + resource + "', ";
        assertSqlState("23514", stock + UUID.randomUUID() + references + "10, 11, 0)");
        assertSqlState("23514", stock + UUID.randomUUID() + references + "-1, 0, 0)");
        execute(stock + UUID.randomUUID() + references + "10, 0, 0)");
        assertSqlState("23505", stock + UUID.randomUUID() + references + "10, 0, 0)");
        assertSqlState("23503", stock + UUID.randomUUID() + "', now(), now(), '" + UUID.randomUUID()
                + "', '" + resource + "', 10, 0, 0)");
    }

    @Test
    void nonEmptyUnmanagedSchemaIsRejectedWithoutLosingExistingData() throws Exception {
        execute("CREATE TABLE " + schema + ".legacy_record (id integer PRIMARY KEY)");
        execute("INSERT INTO " + schema + ".legacy_record VALUES (1)");
        assertThrows(FlywayException.class, () -> flyway().migrate());
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".legacy_record"));
        assertEquals(0, scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema = '"
                + schema + "' AND table_name = 'flyway_schema_history'"));
    }

    @Test
    void changedMigrationChecksumIsRejected() throws Exception {
        flyway().migrate();
        execute("UPDATE " + schema + ".flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
        assertThrows(FlywayException.class, () -> flyway().validate());
    }

    @Test
    void hibernateRefusesSchemaDriftInsteadOfRepairingIt() throws Exception {
        flyway().migrate();
        execute("ALTER TABLE " + schema + ".organizations DROP COLUMN name");
        var failure = assertThrows(Exception.class, () -> {
            try (var ignored = startApplication()) {
                fail("Hibernate must reject the missing mapped column");
            }
        });
        StringBuilder messages = new StringBuilder();
        boolean schemaValidationFailure = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
            schemaValidationFailure |= cause instanceof SchemaManagementException;
        }
        assertTrue(schemaValidationFailure, messages.toString());
        assertEquals(0, scalar("SELECT count(*) FROM information_schema.columns WHERE table_schema = '"
                + schema + "' AND table_name = 'organizations' AND column_name = 'name'"));
    }

    private ConfigurableApplicationContext startApplication() throws Exception {
        var production = PropertiesLoaderUtils.loadProperties(
                new FileSystemResource("src/main/resources/application.properties"));
        var overrides = new HashMap<String, Object>();
        overrides.put("spring.datasource.url", url);
        overrides.put("spring.datasource.username", user);
        overrides.put("spring.datasource.password", password);
        overrides.put("spring.datasource.hikari.schema", schema);
        overrides.put("spring.jpa.properties.hibernate.default_schema", schema);
        overrides.put("spring.flyway.schemas", schema);
        overrides.put("spring.flyway.default-schema", schema);
        overrides.put("server.address", "127.0.0.1");
        overrides.put("server.port", "0");
        var application = new SpringApplication(DisasterReliefBackendApplication.class);
        application.addInitializers(context -> {
            var sources = context.getEnvironment().getPropertySources();
            sources.addFirst(new PropertiesPropertySource("production-config-under-test", production));
            sources.addFirst(new MapPropertySource("disposable-postgresql-test", overrides));
        });
        return application.run();
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").baselineOnMigrate(false).cleanDisabled(true).load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void execute(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalar(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void assertSqlState(String state, String sql) {
        assertEquals(state, assertThrows(SQLException.class, () -> execute(sql)).getSQLState());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set to a dedicated PostgreSQL test database");
        }
        return value;
    }
}
