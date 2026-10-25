package com.disasterrelief.allocator;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.service.*;
import com.disasterrelief.allocator.support.TestIdentityProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhaseFiveWorkspaceTests {
    private static final TestIdentityProvider provider;
    static { try { provider = new TestIdentityProvider(); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("security.oauth2.issuer-uri", () -> TestIdentityProvider.ISSUER);
        registry.add("security.oauth2.audience", () -> TestIdentityProvider.AUDIENCE);
        registry.add("security.oauth2.jwk-set-uri", provider::jwksUri);
    }
    @AfterAll static void closeProvider() { provider.close(); }
    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired UserProfileRepository users;
    @Autowired LocationService locations;
    @Autowired ResourceService resources;
    @Autowired DisasterService disasters;
    @Autowired RequestService requests;
    @Autowired AllocationService allocations;

    @Test void anonymousWorkspaceRequestsRequireAuthentication() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/workspace/overview")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/workspace/inventory")).andExpect(status().isUnauthorized());
    }

    @Test void stockSearchPaginationSortingAndCountsStayWithinTheOrganization() throws Exception {
        var own = organization("Own"); var foreign = organization("Foreign");
        var manager = user(own, Role.INVENTORY_MANAGER);
        var water = resources.createResourceType("Water", "litres", false);
        var rice = resources.createResourceType("Rice", "kg", true);
        var a = locations.create("Own depot", "Address", own.getId(), null, null);
        var b = locations.create("Foreign depot", "Other address", foreign.getId(), null, null);
        resources.receive(a.getId(), water.getId(), new BigDecimal("10"));
        resources.receive(a.getId(), rice.getId(), new BigDecimal("30"));
        resources.receive(b.getId(), water.getId(), new BigDecimal("1000"));
        get(manager, "inventory?size=1&sort=quantity&direction=desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].quantity").value(30))
                .andExpect(jsonPath("$.content[0].locationName").value("Own depot"));
        get(manager, "inventory?q=wAtEr&locationId=" + a.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].resourceName").value("Water"));
        get(manager, "overview").andExpect(status().isOk()).andExpect(jsonPath("$.inventoryLines").value(2))
                .andExpect(jsonPath("$.locations").value(1));
        get(manager, "inventory?organizationId=" + foreign.getId()).andExpect(status().isForbidden());
        get(manager, "overview?organizationId=" + foreign.getId()).andExpect(status().isForbidden());
    }

    @Test void readOnlyMembersCanBrowseTheirOwnCoordinationDataButNotStockOrAdministration() throws Exception {
        var own = organization("Own"); var foreign = organization("Foreign");
        var viewer = user(own, Role.VIEWER);
        locations.create("Own shelter", "Address", own.getId(), null, null);
        locations.create("Other shelter", "Address", foreign.getId(), null, null);
        disasters.createForOrganization("Own flood", DisasterType.FLOOD, null, Instant.now(), own.getId());
        disasters.createForOrganization("Other flood", DisasterType.FLOOD, null, Instant.now(), foreign.getId());
        get(viewer, "locations").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        get(viewer, "disasters?status=DRAFT").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Own flood"));
        for (String collection : List.of("inventory", "users", "organizations")) {
            get(viewer, collection).andExpect(status().isForbidden());
        }
    }

    @Test void administrationQueriesAreAdministratorOnlyAndAllowCrossOrganizationBrowsing() throws Exception {
        var first = organization("Alpha"); var second = organization("Beta");
        user(first, Role.VIEWER); user(second, Role.FIELD_AGENT); var admin = user(null, Role.SYSTEM_ADMIN);
        get(admin, "organizations?sort=name&direction=asc&q=Alpha").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].name").value("Alpha"));
        get(admin, "users?size=100").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        get(admin, "locations").andExpect(status().isOk());
    }

    @Test void requestAndAllocationRowsHaveReadableDetailsAndDestinationFilters() throws Exception {
        var own = organization("Own"); var manager = user(own, Role.INVENTORY_MANAGER);
        var destination = locations.create("Shelter", "Address", own.getId(), null, null);
        var other = locations.create("Other", "Address", own.getId(), null, null);
        var disaster = disasters.createForOrganization("Flood", DisasterType.FLOOD, null, Instant.now(), own.getId());
        disasters.activate(disaster.getId()); disasters.addAffectedLocation(disaster.getId(), destination.getId());
        var type = resources.createResourceType("Medical kits", "kits", false);
        resources.receive(destination.getId(), type.getId(), new BigDecimal("20"));
        var request = requests.create("FRONTEND-001", disaster.getId(), manager.getId(), destination.getId(), RequestPriority.HIGH, null);
        requests.addItem(request.getId(), type.getId(), new BigDecimal("5"));
        requests.submit(request.getId()); requests.approve(request.getId()); allocations.allocate(request.getId());
        get(manager, "requests?q=FRONTEND").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].destinationName").value("Shelter"))
                .andExpect(jsonPath("$.content[0].request.items[0].resourceName").value("Medical kits"))
                .andExpect(jsonPath("$.content[0].request.items[0].deliveredQuantity").value(0));
        get(manager, "allocations?status=RESERVED&locationId=" + destination.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].resourceName").value("Medical kits"));
        get(manager, "allocations?locationId=" + other.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void invalidQueryBoundsAndUnrecognizedSortFieldsAreRejected() throws Exception {
        var manager = user(organization("Own"), Role.INVENTORY_MANAGER);
        for (String query : List.of("size=101", "page=-1", "page=100001", "sort=password", "direction=invalid", "status=invalid")) {
            get(manager, "inventory?" + query).andExpect(status().isBadRequest());
        }
        get(manager, "unknown").andExpect(status().isBadRequest());
    }

    @Test void wildcardSearchIsLiteralAndMarkupNamesRemainPlainResponseData() throws Exception {
        var manager = user(organization("Own"), Role.INVENTORY_MANAGER);
        resources.createResourceType("Water 100%", "litres", false);
        resources.createResourceType("<img src=x onerror=alert(1)>", "units", false);
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/workspace/resource-types").param("q", "%")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token(manager.getExternalSubject())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        get(manager, "resource-types?q=img").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("<img src=x onerror=alert(1)>"));
    }

    private Organization organization(String name) { var o = new Organization(); o.setName(name); return organizations.saveAndFlush(o); }
    private UserProfile user(Organization o, Role role) {
        var u = new UserProfile(); u.setExternalIssuer(TestIdentityProvider.ISSUER);
        u.setExternalSubject("workspace-" + UUID.randomUUID()); u.setDisplayName("Workspace person");
        u.setOrganization(o); u.setRoles(new HashSet<>(Set.of(role))); return users.saveAndFlush(u);
    }
    private ResultActions get(UserProfile user, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/workspace/" + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token(user.getExternalSubject())));
    }
}
