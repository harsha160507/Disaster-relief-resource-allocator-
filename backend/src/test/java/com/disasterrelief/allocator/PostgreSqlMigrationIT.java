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
            assertEquals(7, flyway.info().applied().length);
            assertEquals(0, flyway.info().pending().length);
            assertEquals(15, scalar("SELECT count(*) FROM information_schema.tables WHERE table_schema = '" + schema + "'"));
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
            assertEquals(7, restarted.getBean(Flyway.class).info().applied().length);
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


    @Test
    void upgradingVersionFourPreservesButDisablesUnboundLegacyIdentities() throws Exception {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("4").load().migrate();
        UUID id = UUID.randomUUID();
        execute("INSERT INTO " + schema + ".user_profiles "
                + "(id, created_at, updated_at, external_subject, display_name, active) VALUES ('"
                + id + "', now(), now(), 'legacy-subject', 'Legacy User', true)");
        flyway().migrate();
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".user_profiles WHERE id = '"
                + id + "' AND active = false AND external_issuer IS NULL"));
        assertEquals(7, flyway().info().applied().length);
    }

    @Test
    void bootstrapAndLastAdministratorProtectionSerializeAcrossConcurrentTransactions() throws Exception {
        try (var provider = new com.disasterrelief.allocator.support.TestIdentityProvider();
                var context = startApplication(Map.of(
                    "security.oauth2.issuer-uri", com.disasterrelief.allocator.support.TestIdentityProvider.ISSUER,
                    "security.oauth2.audience", com.disasterrelief.allocator.support.TestIdentityProvider.AUDIENCE,
                    "security.oauth2.jwk-set-uri", provider.jwksUri()))) {
            var service = context.getBean(com.disasterrelief.allocator.service.AdministrationService.class);
            var start = new java.util.concurrent.CountDownLatch(1);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> { start.await(); return service.bootstrap("first", "First Admin"); });
                var second = pool.submit(() -> { start.await(); return service.bootstrap("second", "Second Admin"); });
                start.countDown();
                assertNotEquals(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                        second.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            var repository = context.getBean(UserProfileRepository.class);
            var initial = repository.findAll().getFirst();
            var initialView = service.user(initial.getId());
            var replacement = service.createUser(new com.disasterrelief.allocator.api.dto.AdministrationDtos.UserCreate(
                    com.disasterrelief.allocator.support.TestIdentityProvider.ISSUER,
                    "replacement", "Replacement", null, null, Set.of(Role.SYSTEM_ADMIN)));
            var race = new java.util.concurrent.CountDownLatch(1);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> disableAdministrator(service, initialView, race));
                var second = pool.submit(() -> disableAdministrator(service, replacement, race));
                race.countDown();
                assertNotEquals(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                        second.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(1, repository.countByActiveTrueAndRolesContaining(Role.SYSTEM_ADMIN));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events WHERE source = 'BOOTSTRAP'"));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events WHERE operation = 'user.updated'"));
        }
    }

    @Test
    void versionFiveAuditRowsSurviveUpgradeAndAllMutationFormsAreBlocked() throws Exception {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("5").load().migrate();
        UUID id = UUID.randomUUID();
        execute("INSERT INTO " + schema + ".audit_events "
                + "(id, created_at, updated_at, action, entity_type, entity_id, details) VALUES ('"
                + id + "', now(), now(), 'CREATED', 'legacy_entity', '" + UUID.randomUUID() + "', 'preserved')");
        flyway().migrate();
        assertEquals(1, scalar("SELECT count(*) FROM " + schema
                + ".audit_events WHERE id = '" + id + "' AND source = 'LEGACY' AND details = 'preserved'"));
        // A JDBC connection using the test schema mirrors the production connection search path.
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + schema);
            for (String sql : List.of("UPDATE audit_events SET details = 'changed'",
                    "DELETE FROM audit_events", "TRUNCATE audit_events")) {
                assertEquals("55000", assertThrows(SQLException.class, () -> statement.execute(sql)).getSQLState());
            }
        }
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events"));
    }

    @Test
    void committedAuditSurvivesRestartAndAuditFailureRollsBackBusinessChange() throws Exception {
        try (var context = startApplication()) {
            var service = context.getBean(AdministrationService.class);
            var created = service.createOrganization(new com.disasterrelief.allocator.api.dto.AdministrationDtos.OrganizationCreate(
                    "Committed organization", "committed", null));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema
                    + ".audit_events WHERE entity_id = '" + created.id() + "' AND source = 'SYSTEM_INTERNAL'"));
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                service.createOrganization(new com.disasterrelief.allocator.api.dto.AdministrationDtos.OrganizationCreate(
                        "Rolled back organization", "rollback", null));
                status.setRollbackOnly();
            });
            assertEquals(0, scalar("SELECT count(*) FROM " + schema + ".organizations WHERE code = 'ROLLBACK'"));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events"));

            // Inject an append failure only in this disposable schema.
            execute("ALTER TABLE " + schema + ".audit_events ADD CONSTRAINT test_reject_append CHECK (operation <> 'organization.created') NOT VALID");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                    service.createOrganization(new com.disasterrelief.allocator.api.dto.AdministrationDtos.OrganizationCreate(
                            "Must roll back", "audit_failure", null)));
            assertEquals(0, scalar("SELECT count(*) FROM " + schema + ".organizations WHERE code = 'AUDIT_FAILURE'"));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events"));
            execute("ALTER TABLE " + schema + ".audit_events DROP CONSTRAINT test_reject_append");
        }
        try (var restarted = startApplication()) {
            assertEquals(1, restarted.getBean(com.disasterrelief.allocator.repository.AuditEventRepository.class).count());
        }
    }

    @Test
    void concurrentManifestDispatchAndDeliveryApplyEachTransitionOnceAndSurviveRestart() throws Exception {
        PgShipmentScenario scenario;
        UUID shipmentId;
        try (var context = startApplication()) {
            scenario = shipmentScenario(context, "20");
            var allocation = context.getBean(AllocationService.class).allocate(scenario.request()).getFirst();
            var service = context.getBean(ShipmentService.class);
            var wins = race(() -> service.create(manifest(scenario, allocation.getId())),
                    () -> service.create(manifest(scenario, allocation.getId())));
            assertEquals(1, wins);
            shipmentId = context.getBean(com.disasterrelief.allocator.repository.ShipmentRepository.class).findAll().getFirst().getId();
            UUID id = shipmentId;
            assertEquals(1, race(() -> service.dispatch(id), () -> service.dispatch(id)));
            assertPgStock(scenario, "30", "0");
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events WHERE operation = 'inventory.dispatched'"));
            assertEquals(1, race(() -> service.deliver(id), () -> service.deliver(id)));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_requests WHERE id = '"
                    + scenario.request() + "' AND status = 'DELIVERED'"));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_request_items WHERE id = '"
                    + scenario.item() + "' AND delivered_quantity = 20 AND allocated_quantity = 20"));
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events WHERE operation = 'request_item.delivered'"));
        }
        try (var restarted = startApplication()) {
            assertEquals(ShipmentStatus.DELIVERED, restarted.getBean(ShipmentService.class).get(shipmentId).status());
            assertPgStock(scenario, "30", "0");
        }
    }

    @Test
    void concurrentDispatchAndCancellationHaveOneWinnerWithoutDoubleCounting() throws Exception {
        try (var context = startApplication()) {
            var scenario = shipmentScenario(context, "20");
            var allocation = context.getBean(AllocationService.class).allocate(scenario.request()).getFirst();
            var service = context.getBean(ShipmentService.class);
            UUID id = service.create(manifest(scenario, allocation.getId())).id();
            assertEquals(1, race(() -> service.dispatch(id), () -> service.cancel(id)));
            var state = service.get(id).status();
            if (state == ShipmentStatus.DISPATCHED) {
                assertPgStock(scenario, "30", "0");
                assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_request_items WHERE id = '"
                        + scenario.item() + "' AND allocated_quantity = 20"));
            } else {
                assertEquals(ShipmentStatus.CANCELLED, state);
                assertPgStock(scenario, "50", "0");
                assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_request_items WHERE id = '"
                        + scenario.item() + "' AND allocated_quantity = 0 AND delivered_quantity = 0"));
            }
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".audit_events WHERE operation IN "
                    + "('inventory.dispatched', 'inventory.reservation_released')"));
        }
    }

    @Test
    void allocatingWhileAnotherShipmentDispatchesConservesAvailableStock() throws Exception {
        try (var context = startApplication()) {
            var scenario = shipmentScenario(context, "20");
            var allocationService = context.getBean(AllocationService.class);
            var allocation = allocationService.allocate(scenario.request()).getFirst();
            var shipmentService = context.getBean(ShipmentService.class);
            UUID shipment = shipmentService.create(manifest(scenario, allocation.getId())).id();
            var requestService = context.getBean(RequestService.class);
            var request = requestService.create("CONCURRENT-OTHER", scenario.disaster(), scenario.requester(),
                    scenario.destination(), RequestPriority.HIGH, null);
            requestService.addItem(request.getId(), scenario.resource(), new BigDecimal("40"));
            requestService.submit(request.getId()); requestService.approve(request.getId());
            assertEquals(2, race(() -> shipmentService.dispatch(shipment), () -> allocationService.allocate(request.getId())));
            assertPgStock(scenario, "30", "30");
            assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_request_items WHERE request_id = '"
                    + request.getId() + "' AND allocated_quantity = 30 AND delivered_quantity = 0"));
            assertEquals(RequestStatus.PARTIALLY_FULFILLED, requestService.find(request.getId()).getStatus());
        }
    }

    @Test
    void failedShipmentAuditAppendRollsBackStockAllocationAndShipmentTogether() throws Exception {
        try (var context = startApplication()) {
            var scenario = shipmentScenario(context, "20");
            var allocation = context.getBean(AllocationService.class).allocate(scenario.request()).getFirst();
            var service = context.getBean(ShipmentService.class);
            UUID id = service.create(manifest(scenario, allocation.getId())).id();
            long auditCount = scalar("SELECT count(*) FROM " + schema + ".audit_events");
            execute("ALTER TABLE " + schema + ".audit_events ADD CONSTRAINT test_reject_shipment_append "
                    + "CHECK (operation <> 'shipment.status_changed') NOT VALID");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.dispatch(id));
            assertPgStock(scenario, "50", "20");
            assertEquals(ShipmentStatus.CREATED, service.get(id).status());
            assertEquals(AllocationStatus.CONFIRMED, service.get(id).allocations().getFirst().status());
            assertEquals(auditCount, scalar("SELECT count(*) FROM " + schema + ".audit_events"));
            execute("ALTER TABLE " + schema + ".audit_events DROP CONSTRAINT test_reject_shipment_append");
            service.dispatch(id); service.deliver(id);
            assertPgStock(scenario, "30", "0");
        }
    }

    @Test
    void versionSixDeliveredRecordsUpgradeWithoutDeductingStockAgain() throws Exception {
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("6").load().migrate();
        UUID org = UUID.randomUUID(), location = UUID.randomUUID(), userId = UUID.randomUUID(),
                resource = UUID.randomUUID(), disaster = UUID.randomUUID(), request = UUID.randomUUID(),
                item = UUID.randomUUID(), inventory = UUID.randomUUID(), shipment = UUID.randomUUID();
        execute("INSERT INTO " + schema + ".organizations (id,created_at,updated_at,name,active) VALUES ('"
                + org + "',now(),now(),'Legacy org',true)");
        execute("INSERT INTO " + schema + ".locations (id,created_at,updated_at,name,address,organization_id) VALUES ('"
                + location + "',now(),now(),'Legacy shelter','Address','" + org + "')");
        execute("INSERT INTO " + schema + ".user_profiles (id,created_at,updated_at,external_subject,display_name,active) VALUES ('"
                + userId + "',now(),now(),'legacy','Legacy user',false)");
        execute("INSERT INTO " + schema + ".resource_types (id,created_at,updated_at,name,unit_of_measure,perishable,active) VALUES ('"
                + resource + "',now(),now(),'Water','litres',false,true)");
        execute("INSERT INTO " + schema + ".disasters (id,created_at,updated_at,name,type,status,started_at) VALUES ('"
                + disaster + "',now(),now(),'Flood','FLOOD','ACTIVE',now())");
        execute("INSERT INTO " + schema + ".resource_requests "
                + "(id,created_at,updated_at,reference,disaster_id,requested_by_id,destination_location_id,status,priority) VALUES ('"
                + request + "',now(),now(),'LEGACY-DELIVERED','" + disaster + "','" + userId + "','" + location + "','FULFILLED','HIGH')");
        execute("INSERT INTO " + schema + ".resource_request_items "
                + "(id,created_at,updated_at,request_id,resource_type_id,requested_quantity,allocated_quantity,version) VALUES ('"
                + item + "',now(),now(),'" + request + "','" + resource + "',5,5,0)");
        execute("INSERT INTO " + schema + ".inventory "
                + "(id,created_at,updated_at,location_id,resource_type_id,quantity,reserved_quantity,version) VALUES ('"
                + inventory + "',now(),now(),'" + location + "','" + resource + "',50,0,0)");
        execute("INSERT INTO " + schema + ".shipments "
                + "(id,created_at,updated_at,destination_location_id,status,tracking_reference,dispatched_at,delivered_at) VALUES ('"
                + shipment + "',now(),now(),'" + location + "','DELIVERED','LEGACY-TRACK',now(),now())");
        execute("INSERT INTO " + schema + ".allocations "
                + "(id,created_at,updated_at,request_item_id,inventory_id,quantity,status,shipment_id) VALUES ('"
                + UUID.randomUUID() + "',now(),now(),'" + item + "','" + inventory + "',5,'DELIVERED','" + shipment + "')");
        flyway().migrate();
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_request_items WHERE id = '" + item
                + "' AND delivered_quantity = 5 AND allocated_quantity = 5"));
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".resource_requests WHERE id = '" + request + "' AND status = 'DELIVERED'"));
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".inventory WHERE id = '" + inventory + "' AND quantity = 50"));
        assertEquals(7, flyway().info().applied().length);
        assertSqlState("23514", "UPDATE " + schema + ".resource_request_items SET delivered_quantity = 6 WHERE id = '" + item + "'");
        assertSqlState("23514", "UPDATE " + schema + ".shipments SET delivered_at = dispatched_at - interval '1 second' WHERE id = '" + shipment + "'");
    }

    private record PgShipmentScenario(UUID request, UUID item, UUID inventory, UUID destination,
            UUID resource, UUID disaster, UUID requester) {}

    private PgShipmentScenario shipmentScenario(ConfigurableApplicationContext context, String quantity) {
        var organization = new Organization(); organization.setName("Shipment integration org");
        organization = context.getBean(OrganizationRepository.class).saveAndFlush(organization);
        var requester = new UserProfile(); requester.setExternalSubject("shipment-it");
        requester.setDisplayName("Shipment integration requester"); requester.setOrganization(organization);
        requester = context.getBean(UserProfileRepository.class).saveAndFlush(requester);
        var locations = context.getBean(LocationService.class);
        var disasters = context.getBean(DisasterService.class);
        var resources = context.getBean(ResourceService.class);
        var requests = context.getBean(RequestService.class);
        var location = locations.create("Shelter", "Address", organization.getId(), null, null);
        var disaster = disasters.createForOrganization("Flood", DisasterType.FLOOD, null, Instant.now(), organization.getId());
        disasters.activate(disaster.getId()); disasters.addAffectedLocation(disaster.getId(), location.getId());
        var resource = resources.createResourceType("Water", "litres", false);
        var inventory = resources.receive(location.getId(), resource.getId(), new BigDecimal("50"));
        var request = requests.create("SHIPMENT-PG", disaster.getId(), requester.getId(), location.getId(), RequestPriority.HIGH, null);
        requests.addItem(request.getId(), resource.getId(), new BigDecimal(quantity));
        requests.submit(request.getId()); requests.approve(request.getId());
        UUID itemId = context.getBean(com.disasterrelief.allocator.repository.ResourceRequestItemRepository.class)
                .findByRequestId(request.getId()).getFirst().getId();
        return new PgShipmentScenario(request.getId(), itemId, inventory.getId(), location.getId(),
                resource.getId(), disaster.getId(), requester.getId());
    }

    private com.disasterrelief.allocator.api.dto.ShipmentDtos.Create manifest(PgShipmentScenario scenario, UUID allocation) {
        return new com.disasterrelief.allocator.api.dto.ShipmentDtos.Create(scenario.destination(), "PG-" + UUID.randomUUID(), List.of(allocation));
    }

    private int race(Runnable first, Runnable second) throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> raceOperation(start, first));
            var b = pool.submit(() -> raceOperation(start, second));
            start.countDown();
            return a.get(30, java.util.concurrent.TimeUnit.SECONDS) + b.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private int raceOperation(java.util.concurrent.CountDownLatch start, Runnable operation) throws Exception {
        start.await();
        try { operation.run(); return 1; }
        catch (com.disasterrelief.allocator.exception.BusinessRuleViolationException expected) { return 0; }
    }

    private void assertPgStock(PgShipmentScenario scenario, String quantity, String reserved) throws Exception {
        assertEquals(1, scalar("SELECT count(*) FROM " + schema + ".inventory WHERE id = '" + scenario.inventory()
                + "' AND quantity = " + quantity + " AND reserved_quantity = " + reserved));
    }

    private boolean disableAdministrator(com.disasterrelief.allocator.service.AdministrationService service,
            com.disasterrelief.allocator.api.dto.AdministrationDtos.UserView user,
            java.util.concurrent.CountDownLatch start) throws Exception {
        start.await();
        try {
            service.updateUser(user.id(), new com.disasterrelief.allocator.api.dto.AdministrationDtos.UserUpdate(
                    user.displayName(), user.email(), null, Set.of(Role.SYSTEM_ADMIN), false, user.version()));
            return true;
        } catch (com.disasterrelief.allocator.exception.BusinessRuleViolationException expected) {
            return false;
        }
    }

    private ConfigurableApplicationContext startApplication() throws Exception {
        return startApplication(Map.of());
    }

    private ConfigurableApplicationContext startApplication(Map<String, Object> additionalProperties) throws Exception {
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
        overrides.putAll(additionalProperties);
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
