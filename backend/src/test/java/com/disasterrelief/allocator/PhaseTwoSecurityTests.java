package com.disasterrelief.allocator;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.service.*;
import com.disasterrelief.allocator.support.TestIdentityProvider;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhaseTwoSecurityTests {
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
    @AfterAll static void stopProvider() { provider.close(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrganizationRepository organizations;
    @Autowired UserProfileRepository users;
    @Autowired AdministrationLockRepository guards;
    @Autowired AdministrationService administration;
    @Autowired LocationService locations;
    @Autowired DisasterService disasters;
    @Autowired ResourceService resources;
    @Autowired RequestService requests;

    @BeforeEach void guard() {
        if (!guards.existsById(1)) guards.saveAndFlush(new AdministrationLock(1));
    }

    @Test void administratorOnboardsOrganizationAndUserAndUserCanReadOwnProfile() throws Exception {
        var admin = administrator();
        var organization = body(call(admin, "POST", "/api/v1/admin/organizations",
                Map.of("name", "Relief North", "code", "north", "contactEmail", "office@example.test"))
                .andExpect(status().isCreated()));
        String subject = "new-" + UUID.randomUUID();
        var user = body(call(admin, "POST", "/api/v1/admin/users", Map.of(
                "issuer", TestIdentityProvider.ISSUER, "subject", subject, "displayName", "Field Agent",
                "organizationId", organization.get("id"), "roles", List.of("FIELD_AGENT")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("FIELD_AGENT")));
        call(provider.token(subject), "GET", "/api/v1/me", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.get("id")))
                .andExpect(jsonPath("$.organizationId").value(organization.get("id")));
        call(provider.token(subject), "GET", "/api/v1/me/organization", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NORTH"));
        call(admin, "GET", "/api/v1/admin/users?size=1", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        call(admin, "GET", "/api/v1/admin/organizations", null).andExpect(status().isOk());
        call(provider.token(subject), "GET", "/api/v1/admin/users", null).andExpect(status().isForbidden());
    }

    @Test void tokenAdminClaimsCannotGrantLocalAdministratorPermissions() throws Exception {
        var viewer = member(organization(), Role.VIEWER);
        call(viewer, "GET", "/api/v1/me", null).andExpect(status().isOk());
        call(viewer, "POST", "/api/v1/admin/organizations", Map.of("name", "Unauthorized"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
    }

    @Test void unknownUnboundAndDisabledProfilesAreRejected() throws Exception {
        call(provider.token("unknown"), "GET", "/api/v1/me", null).andExpect(status().isUnauthorized());
        var user = member(organization(), Role.FIELD_AGENT);
        user.setExternalIssuer(null);
        users.saveAndFlush(user);
        call(user, "GET", "/api/v1/me", null).andExpect(status().isUnauthorized());
        user.setExternalIssuer(TestIdentityProvider.ISSUER);
        user.setActive(false);
        users.saveAndFlush(user);
        call(user, "GET", "/api/v1/me", null).andExpect(status().isUnauthorized());
    }

    @Test void wrongIssuerAudienceExpiredAndMissingClaimsAreRejectedByRealJwtDecoder() throws Exception {
        var user = member(organization(), Role.VIEWER);
        String subject = user.getExternalSubject();
        for (String token : List.of(
                provider.token(subject, b -> b.issuer("https://other.example.test")),
                provider.token(subject, b -> b.audience("other-api")),
                provider.token(subject, b -> b.audience((String) null)),
                provider.token(subject, b -> b.expirationTime(Date.from(Instant.now().minusSeconds(3600)))),
                provider.token(subject, b -> b.expirationTime(null)),
                provider.token(subject, b -> b.subject(null)))) {
            call(token, "GET", "/api/v1/me", null).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test void aDifferentSigningKeyCannotAuthenticate() throws Exception {
        var user = member(organization(), Role.VIEWER);
        try (var untrusted = new TestIdentityProvider()) {
            call(untrusted.token(user.getExternalSubject()), "GET", "/api/v1/me", null)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test void duplicateCodesAndIdentitiesConflictAndInvalidInputIsRejected() throws Exception {
        var admin = administrator();
        call(admin, "POST", "/api/v1/admin/organizations", Map.of("name", "First", "code", "same"))
                .andExpect(status().isCreated());
        call(admin, "POST", "/api/v1/admin/organizations", Map.of("name", "Second", "code", "SAME"))
                .andExpect(status().isConflict());
        call(admin, "POST", "/api/v1/admin/organizations", Map.of("name", "Bad", "contactEmail", "bad-email"))
                .andExpect(status().isBadRequest());
        call(admin, "POST", "/api/v1/admin/users", Map.of("issuer", TestIdentityProvider.ISSUER,
                "subject", admin.getExternalSubject(), "displayName", "Duplicate", "roles", List.of("SYSTEM_ADMIN")))
                .andExpect(status().isConflict());
        call(admin, "POST", "/api/v1/admin/users", Map.of("issuer", TestIdentityProvider.ISSUER,
                "subject", "invalid", "displayName", "Invalid", "roles", List.of("UNKNOWN")))
                .andExpect(status().isBadRequest());
        call(admin, "POST", "/api/v1/admin/users", Map.of("issuer", "https://untrusted.example.test",
                "subject", "invalid", "displayName", "Invalid", "roles", List.of("SYSTEM_ADMIN")))
                .andExpect(status().isUnprocessableEntity());
        call(admin, "GET", "/api/v1/admin/users?size=101", null).andExpect(status().isBadRequest());
        call(admin, "GET", "/api/v1/admin/organizations?page=-1", null).andExpect(status().isBadRequest());
    }

    @Test void staleOrganizationAndUserEditsReturnConflict() throws Exception {
        var admin = administrator();
        var organization = organization();
        var user = member(organization, Role.VIEWER);
        call(admin, "PUT", "/api/v1/admin/organizations/" + organization.getId(),
                Map.of("name", "Changed", "active", true, "version", 999)).andExpect(status().isConflict());
        var update = update(user, true, Set.of(Role.VIEWER));
        update.put("version", 999);
        call(admin, "PUT", "/api/v1/admin/users/" + user.getId(), update).andExpect(status().isConflict());
    }

    @Test void lastAdministratorIsProtectedAndAReplacementAllowsDeactivation() throws Exception {
        var admin = administrator();
        var update = update(admin, false, Set.of(Role.SYSTEM_ADMIN));
        call(admin, "PUT", "/api/v1/admin/users/" + admin.getId(), update)
                .andExpect(status().isUnprocessableEntity());
        administrator();
        String originalToken = provider.token(admin.getExternalSubject());
        call(admin, "PUT", "/api/v1/admin/users/" + admin.getId(), update).andExpect(status().isOk());
        call(originalToken, "GET", "/api/v1/me", null).andExpect(status().isUnauthorized());
    }

    @Test void existingTokensObserveRoleRevocationAndProfileDeactivation() throws Exception {
        var admin = administrator();
        var user = member(organization(), Role.INVENTORY_MANAGER);
        String token = provider.token(user.getExternalSubject());
        call(token, "GET", "/api/v1/inventory", null).andExpect(status().isNotFound());
        var changed = body(call(admin, "PUT", "/api/v1/admin/users/" + user.getId(),
                update(user, true, Set.of(Role.VIEWER))).andExpect(status().isOk()));
        call(token, "GET", "/api/v1/inventory", null).andExpect(status().isForbidden());
        var disabled = update(user, false, Set.of(Role.VIEWER));
        disabled.put("version", changed.get("version"));
        call(admin, "PUT", "/api/v1/admin/users/" + user.getId(), disabled).andExpect(status().isOk());
        call(token, "GET", "/api/v1/me", null).andExpect(status().isUnauthorized());
    }

    @Test void disablingAnOrganizationImmediatelyRejectsItsExistingTokens() throws Exception {
        var admin = administrator();
        var organization = organization();
        var user = member(organization, Role.FIELD_AGENT);
        String token = provider.token(user.getExternalSubject());
        call(token, "GET", "/api/v1/me", null).andExpect(status().isOk());
        call(admin, "PUT", "/api/v1/admin/organizations/" + organization.getId(),
                Map.of("name", organization.getName(), "active", false, "version", organization.getVersion()))
                .andExpect(status().isOk());
        call(token, "GET", "/api/v1/me", null).andExpect(status().isUnauthorized());
    }

    @Test void recordsFromAnotherOrganizationCannotBeReadOrChanged() throws Exception {
        var own = organization();
        var foreign = organization();
        var resource = resources.createResourceType("Water " + UUID.randomUUID(), "litres", false);
        var user = member(own, Role.DISASTER_COORDINATOR, Role.INVENTORY_MANAGER, Role.FIELD_AGENT, Role.PARTNER_MANAGER);
        var other = member(foreign, Role.FIELD_AGENT);
        var scenario = scenario(foreign, other, resource, "50", "20");
        for (String path : List.of("/locations/" + scenario.location().getId(),
                "/disasters/" + scenario.disaster().getId(), "/requests/" + scenario.request().getId(),
                "/inventory/" + scenario.inventory().getId(),
                "/allocations/request-items/" + scenario.itemId())) {
            call(user, "GET", "/api/v1" + path, null).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
        }
        call(user, "PATCH", "/api/v1/inventory/" + scenario.inventory().getId(), Map.of("change", 5))
                .andExpect(status().isForbidden());
        call(user, "POST", "/api/v1/disasters/" + scenario.disaster().getId() + "/activation", null)
                .andExpect(status().isForbidden());
        assertEquals(0, resources.findInventory(scenario.inventory().getId()).getQuantity().compareTo(new BigDecimal("50")));
    }

    @Test void creationCannotSelectAnotherOrganizationOrImpersonateAnotherRequester() throws Exception {
        var own = organization();
        var other = organization();
        var user = member(own, Role.DISASTER_COORDINATOR, Role.PARTNER_MANAGER, Role.FIELD_AGENT);
        call(user, "POST", "/api/v1/locations",
                Map.of("name", "Bad", "address", "Bad", "organizationId", other.getId())).andExpect(status().isForbidden());
        call(user, "POST", "/api/v1/disasters", Map.of("name", "Bad", "type", "FLOOD",
                "startedAt", "2026-10-24T08:00:00Z", "organizationId", other.getId())).andExpect(status().isForbidden());
        var differentRequester = member(own, Role.FIELD_AGENT);
        var resource = resources.createResourceType("Resource " + UUID.randomUUID(), "units", false);
        var scenario = scenario(own, differentRequester, resource, "10", "5");
        call(user, "POST", "/api/v1/requests", Map.of("reference", "IMPERSONATION",
                "requesterId", differentRequester.getId(), "destinationId", scenario.location().getId(),
                "disasterId", scenario.disaster().getId(), "priority", "HIGH")).andExpect(status().isForbidden());
    }

    @Test void fieldAgentsCanEditOnlyTheirOwnRequestsButPartnerManagersCanManageTheirOrganization() throws Exception {
        var organization = organization();
        var first = member(organization, Role.FIELD_AGENT);
        var second = member(organization, Role.FIELD_AGENT);
        var manager = member(organization, Role.PARTNER_MANAGER);
        var resource = resources.createResourceType("Resource " + UUID.randomUUID(), "units", false);
        var scenario = scenario(organization, first, resource, "10", "5");
        String path = "/api/v1/requests/" + scenario.request().getId() + "/submission";
        call(second, "POST", path, null).andExpect(status().isForbidden());
        call(manager, "POST", path, null).andExpect(status().isOk());
    }

    @Test void availabilityAndAllocationNeverUseOtherOrganizationsStock() throws Exception {
        var own = organization();
        var foreign = organization();
        var user = member(own, Role.FIELD_AGENT, Role.INVENTORY_MANAGER);
        var other = member(foreign, Role.FIELD_AGENT);
        var resource = resources.createResourceType("Scoped water " + UUID.randomUUID(), "litres", false);
        var scenario = scenario(own, user, resource, "10", "20");
        var foreignScenario = scenario(foreign, other, resource, "90", "20");
        call(user, "GET", "/api/v1/inventory/resource-types/" + resource.getId() + "/available", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(scenario.inventory().getId().toString()));
        requests.submit(scenario.request().getId());
        requests.approve(scenario.request().getId());
        call(user, "POST", "/api/v1/allocations", Map.of("requestItemId", scenario.itemId(),
                "inventoryId", foreignScenario.inventory().getId(), "quantity", 1)).andExpect(status().isForbidden());
        call(user, "POST", "/api/v1/allocations/requests/" + scenario.request().getId(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(10));
        assertEquals(RequestStatus.PARTIALLY_FULFILLED, requests.find(scenario.request().getId()).getStatus());
        assertEquals(0, resources.findInventory(foreignScenario.inventory().getId()).getReservedQuantity().signum());
    }

    @Test void bootstrapCreatesOnlyTheFirstAdministratorAndNeverPromotesAnExistingMember() {
        assertTrue(administration.bootstrap("initial-admin", "Initial Administrator"));
        assertFalse(administration.bootstrap("another-admin", "Other Administrator"));
        assertEquals(1, users.countByActiveTrueAndRolesContaining(Role.SYSTEM_ADMIN));
    }

    @Test void bootstrapRefusesToPromoteAPreexistingNonAdministratorIdentity() {
        var member = member(organization(), Role.VIEWER);
        assertThrows(IllegalStateException.class, () ->
                administration.bootstrap(member.getExternalSubject(), "Should not promote"));
        assertEquals(0, users.countByActiveTrueAndRolesContaining(Role.SYSTEM_ADMIN));
    }

    private Organization organization() {
        var value = new Organization(); value.setName("Organization " + UUID.randomUUID());
        return organizations.saveAndFlush(value);
    }
    private UserProfile administrator() { return member(null, Role.SYSTEM_ADMIN); }
    private UserProfile member(Organization organization, Role... roles) {
        var user = new UserProfile();
        user.setExternalIssuer(TestIdentityProvider.ISSUER);
        user.setExternalSubject("subject-" + UUID.randomUUID());
        user.setDisplayName("Test User");
        user.setOrganization(organization);
        user.setRoles(new HashSet<>(Arrays.asList(roles)));
        return users.saveAndFlush(user);
    }
    private Map<String,Object> update(UserProfile user, boolean active, Set<Role> roles) {
        var input = new HashMap<String,Object>();
        input.put("displayName", user.getDisplayName());
        input.put("organizationId", user.getOrganization() == null ? null : user.getOrganization().getId());
        input.put("roles", roles); input.put("active", active); input.put("version", user.getVersion());
        return input;
    }
    private ResultActions call(UserProfile user, String method, String path, Object body) throws Exception {
        return call(provider.token(user.getExternalSubject()), method, path, body);
    }
    private ResultActions call(String token, String method, String path, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> body(ResultActions result) throws Exception {
        return json.readValue(result.andReturn().getResponse().getContentAsString(), Map.class);
    }
    private record Scenario(Location location, Disaster disaster, Inventory inventory,
            ResourceRequest request, UUID itemId) {}
    private Scenario scenario(Organization organization, UserProfile requester, ResourceType resource,
            String stock, String quantity) {
        var location = locations.create("Shelter", "Test address", organization.getId(), null, null);
        var disaster = disasters.createForOrganization("Flood", DisasterType.FLOOD, null, Instant.now(), organization.getId());
        disasters.activate(disaster.getId()); disasters.addAffectedLocation(disaster.getId(), location.getId());
        var inventory = resources.receive(location.getId(), resource.getId(), new BigDecimal(stock));
        var request = requests.create("REQ-" + UUID.randomUUID().toString().substring(0, 12),
                disaster.getId(), requester.getId(), location.getId(), RequestPriority.HIGH, null);
        request = requests.addItem(request.getId(), resource.getId(), new BigDecimal(quantity));
        users.flush();
        return new Scenario(location, disaster, inventory, request, request.getItems().getFirst().getId());
    }
}
