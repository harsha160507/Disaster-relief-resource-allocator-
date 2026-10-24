package com.disasterrelief.allocator;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.service.*;
import com.disasterrelief.allocator.security.LocalIdentity;
import com.disasterrelief.allocator.support.TestIdentityProvider;
import com.disasterrelief.allocator.api.dto.AdministrationDtos.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhaseThreeAuditTests {
    private static final TestIdentityProvider provider;
    static {
        try { provider = new TestIdentityProvider(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @DynamicPropertySource static void identityConfiguration(DynamicPropertyRegistry registry) {
        registry.add("security.oauth2.issuer-uri", () -> TestIdentityProvider.ISSUER);
        registry.add("security.oauth2.audience", () -> TestIdentityProvider.AUDIENCE);
        registry.add("security.oauth2.jwk-set-uri", provider::jwksUri);
    }
    @AfterAll static void closeProvider() { provider.close(); }
    @AfterEach void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuditEventRepository events;
    @Autowired OrganizationRepository organizations;
    @Autowired UserProfileRepository users;
    @Autowired AdministrationLockRepository guards;
    @Autowired AdministrationService administration;
    @Autowired LocationService locations;
    @Autowired DisasterService disasters;
    @Autowired ResourceService resources;
    @Autowired RequestService requests;
    @Autowired AllocationService allocations;

    @BeforeEach void guard() {
        if (!guards.existsById(1)) guards.saveAndFlush(new AdministrationLock(1));
    }

    @Test void bearerWriteRecordsTrustedActorAndExcludesPersonalDataAndSpoofedCorrelation() throws Exception {
        var admin = member(null, Role.SYSTEM_ADMIN);
        String token = provider.token(admin.getExternalSubject());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/organizations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Correlation-ID", "00000000-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Private Organization",
                        "contactEmail", "secret@example.test")))).andExpect(status().isCreated());
        var event = events.findAll().getFirst();
        assertEquals("USER", event.getSource());
        assertEquals(admin.getId(), event.getActor().getId());
        assertEquals("SYSTEM_ADMIN", event.getActorRoles());
        assertNull(event.getActorOrganizationId());
        assertEquals(event.getEntityId(), event.getOrganizationId());
        assertEquals("organization.created", event.getOperation());
        assertNotNull(event.getCreatedAt());
        assertNotNull(event.getCorrelationId());
        assertNotEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), event.getCorrelationId());
        for (String privateValue : List.of(token, admin.getExternalSubject(), TestIdentityProvider.ISSUER,
                "Private Organization", "secret@example.test")) assertFalse(event.getDetails().contains(privateValue));
    }

    @Test void allNonAdministratorRolesAndAnonymousCannotReadHistory() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/audit-events")).andExpect(status().isUnauthorized());
        var organization = organization();
        for (Role role : Role.values()) {
            if (role == Role.SYSTEM_ADMIN) continue;
            var member = member(organization, role);
            call(member, "GET", "/api/v1/admin/audit-events", null).andExpect(status().isForbidden());
            call(member, "GET", "/api/v1/admin/audit-events/" + UUID.randomUUID(), null)
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, events.count());
    }

    @Test void administratorCanFilterAndPageHistoryAndCannotMutateIt() throws Exception {
        var admin = member(null, Role.SYSTEM_ADMIN);
        call(admin, "POST", "/api/v1/admin/organizations", Map.of("name", "First")).andExpect(status().isCreated());
        call(admin, "POST", "/api/v1/admin/organizations", Map.of("name", "Second")).andExpect(status().isCreated());
        var event = events.findAll().getFirst();
        String query = "?entityType=ORGANIZATION&entityId=" + event.getEntityId()
                + "&actorId=" + admin.getId() + "&organizationId=" + event.getOrganizationId()
                + "&action=CREATED&correlationId=" + event.getCorrelationId()
                + "&from=2000-01-01T00:00:00Z&to=2100-01-01T00:00:00Z";
        call(admin, "GET", "/api/v1/admin/audit-events" + query, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(event.getId().toString()));
        call(admin, "GET", "/api/v1/admin/audit-events?size=1&page=1", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalElements").value(2));
        call(admin, "GET", "/api/v1/admin/audit-events/" + event.getId(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.actorId").value(admin.getId().toString()));
        call(admin, "GET", "/api/v1/admin/audit-events/" + UUID.randomUUID(), null).andExpect(status().isNotFound());
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            call(admin, method, "/api/v1/admin/audit-events/" + event.getId(), Map.of("details", "changed"))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertEquals(2, events.count());
    }

    @Test void invalidBoundsDatesAndFiltersReturnBadRequest() throws Exception {
        var admin = member(null, Role.SYSTEM_ADMIN);
        for (String query : List.of("page=-1", "page=100001", "size=0", "size=101", "actorId=bad",
                "action=bad", "entityType=bad", "from=bad",
                "from=2100-01-01T00:00:00Z&to=2000-01-01T00:00:00Z")) {
            call(admin, "GET", "/api/v1/admin/audit-events?" + query, null).andExpect(status().isBadRequest());
        }
    }

    @Test void deniedAndInvalidBusinessOperationsCreateNoSuccessEvents() throws Exception {
        var admin = member(null, Role.SYSTEM_ADMIN);
        var viewer = member(organization(), Role.VIEWER);
        call(viewer, "POST", "/api/v1/admin/organizations", Map.of("name", "Denied")).andExpect(status().isForbidden());
        call(admin, "POST", "/api/v1/admin/organizations", Map.of("name", "")).andExpect(status().isBadRequest());
        assertThrows(com.disasterrelief.allocator.exception.BusinessRuleViolationException.class,
                () -> resources.receive(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO));
        assertEquals(0, events.count());
    }

    @Test void userChangesCaptureBeforeAfterRolesAndHistoricalMembershipWithoutPersonalValues() throws Exception {
        var admin = member(null, Role.SYSTEM_ADMIN);
        var first = organization();
        var second = organization();
        var user = member(first, Role.VIEWER);
        call(admin, "PUT", "/api/v1/admin/users/" + user.getId(), Map.of(
                "displayName", "Private renamed user", "email", "private@example.test",
                "organizationId", second.getId(), "roles", List.of("FIELD_AGENT"),
                "active", false, "version", user.getVersion())).andExpect(status().isOk());
        var event = events.findAll().getFirst();
        var details = json.readTree(event.getDetails());
        assertEquals(first.getId().toString(), details.get("before").get("organizationId").asText());
        assertEquals(second.getId().toString(), details.get("after").get("organizationId").asText());
        assertEquals("VIEWER", details.get("before").get("roles").get(0).asText());
        assertEquals("FIELD_AGENT", details.get("after").get("roles").get(0).asText());
        assertFalse(details.get("after").get("active").asBoolean());
        assertTrue(event.getDetails().contains("displayName"));
        assertFalse(event.getDetails().contains("Private renamed user"));
        assertFalse(event.getDetails().contains("private@example.test"));
        assertEquals(second.getId(), event.getOrganizationId());
        assertEquals(admin.getId(), event.getActor().getId());
    }

    @Test void internalAndBootstrapEventsAreExplicitAndNoOpsDoNotProduceHistory() {
        var created = administration.createOrganization(new OrganizationCreate("Internal", "internal", null));
        assertEquals("SYSTEM_INTERNAL", events.findAll().getFirst().getSource());
        long count = events.count();
        administration.updateOrganization(created.id(), new OrganizationUpdate(
                created.name(), created.code(), created.contactEmail(), created.active(), created.version()));
        assertEquals(count, events.count());
        assertTrue(administration.bootstrap("bootstrap-private-subject", "Private bootstrap name"));
        assertFalse(administration.bootstrap("another-private-subject", "Other name"));
        var bootstrap = events.findAll().stream().filter(e -> e.getSource().equals("BOOTSTRAP")).toList();
        assertEquals(1, bootstrap.size());
        assertNull(bootstrap.getFirst().getActor());
        assertFalse(bootstrap.getFirst().getDetails().contains("bootstrap-private-subject"));
    }

    @Test void fullWorkflowCapturesQuantitiesStatusChangesAndCorrelatesAllocationEvents() throws Exception {
        var organization = organization();
        var actor = member(organization, Role.INVENTORY_MANAGER);
        authenticate(actor);
        var location = locations.create("Private shelter", "Private address", organization.getId(), null, null);
        var disaster = disasters.createForOrganization("Private disaster", DisasterType.FLOOD,
                "Private description", Instant.now(), organization.getId());
        disasters.activate(disaster.getId());
        disasters.addAffectedLocation(disaster.getId(), location.getId());
        long beforeNoOp = events.count();
        disasters.addAffectedLocation(disaster.getId(), location.getId());
        assertEquals(beforeNoOp, events.count());
        var resource = resources.createResourceType("Water", "litres", false);
        var inventory = resources.receive(location.getId(), resource.getId(), new BigDecimal("20"));
        resources.adjustQuantity(inventory.getId(), new BigDecimal("-2"));
        var request = requests.create("AUDIT-WORKFLOW", disaster.getId(), actor.getId(), location.getId(),
                RequestPriority.HIGH, "Private request notes");
        requests.addItem(request.getId(), resource.getId(), new BigDecimal("10"));
        requests.submit(request.getId());
        requests.approve(request.getId());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        allocations.reserve(request.getItems().getFirst().getId(), inventory.getId(), new BigDecimal("3"));
        allocations.allocate(request.getId());
        var reservationEvents = events.findAll().stream().filter(e -> e.getOperation().equals("allocation.reserved")).toList();
        assertEquals(2, reservationEvents.size());
        var first = reservationEvents.stream().filter(e -> json.readTree(e.getDetails()).get("quantity").asInt() == 3).findFirst().orElseThrow();
        assertEquals(0, json.readTree(first.getDetails()).get("beforeReservedQuantity").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(3, json.readTree(first.getDetails()).get("afterReservedQuantity").asInt());
        assertNotNull(first.getCorrelationId());
        var related = events.findAll().stream().filter(e -> first.getCorrelationId().equals(e.getCorrelationId())).toList();
        assertEquals(4, related.size()); // two allocations and two fulfillment transitions in this HTTP context
        assertEquals(RequestStatus.FULFILLED, requests.find(request.getId()).getStatus());
        disasters.close(disaster.getId(), Instant.now().plusSeconds(1));
        assertEquals(16, events.count());
        for (var event : events.findAll()) {
            assertEquals("USER", event.getSource());
            assertEquals(actor.getId(), event.getActor().getId());
            assertEquals(organization.getId(), event.getActorOrganizationId());
            assertEquals("INVENTORY_MANAGER", event.getActorRoles());
            for (String privateValue : List.of("Private shelter", "Private address", "Private disaster",
                    "Private description", "Private request notes")) assertFalse(event.getDetails().contains(privateValue));
        }
        actor.setOrganization(organization());
        actor.setRoles(new HashSet<>(Set.of(Role.VIEWER)));
        users.saveAndFlush(actor);
        assertEquals(organization.getId(), first.getActorOrganizationId());
        assertEquals("INVENTORY_MANAGER", first.getActorRoles());
    }

    @Test void requestRejectionIsAuditedAsAStatusChange() {
        var organization = organization();
        var user = member(organization, Role.FIELD_AGENT);
        var location = locations.create("Shelter", "Address", organization.getId(), null, null);
        var disaster = disasters.createForOrganization("Flood", DisasterType.FLOOD, null, Instant.now(), organization.getId());
        disasters.activate(disaster.getId()); disasters.addAffectedLocation(disaster.getId(), location.getId());
        var resource = resources.createResourceType("Food", "kg", false);
        var request = requests.create("REJECT", disaster.getId(), user.getId(), location.getId(), RequestPriority.HIGH, null);
        requests.addItem(request.getId(), resource.getId(), BigDecimal.ONE);
        requests.submit(request.getId()); requests.reject(request.getId());
        assertEquals(1, events.findAll().stream().filter(e -> e.getEntityId().equals(request.getId())
                && e.getDetails().contains("REJECTED") && e.getAction() == AuditAction.STATUS_CHANGED).count());
    }

    private Organization organization() {
        var organization = new Organization(); organization.setName("Fixture " + UUID.randomUUID());
        return organizations.saveAndFlush(organization);
    }
    private UserProfile member(Organization organization, Role role) {
        var user = new UserProfile();
        user.setExternalIssuer(TestIdentityProvider.ISSUER);
        user.setExternalSubject("private-subject-" + UUID.randomUUID());
        user.setDisplayName("Private fixture name");
        user.setOrganization(organization);
        user.setRoles(new HashSet<>(Set.of(role)));
        return users.saveAndFlush(user);
    }
    private void authenticate(UserProfile user) {
        var identity = new LocalIdentity(user.getId(), user.getExternalIssuer(), user.getExternalSubject(),
                user.getOrganization() == null ? null : user.getOrganization().getId(), user.getRoles());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                identity, null, user.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList()));
    }
    private ResultActions call(UserProfile user, String method, String path, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token(user.getExternalSubject()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
}
