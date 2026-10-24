package com.disasterrelief.allocator;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.service.*;
import com.disasterrelief.allocator.api.dto.ShipmentDtos;
import com.disasterrelief.allocator.support.TestIdentityProvider;
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
class PhaseFourShipmentTests {
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

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrganizationRepository organizations;
    @Autowired UserProfileRepository users;
    @Autowired AuditEventRepository events;
    @Autowired ShipmentRepository shipmentRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired LocationService locations;
    @Autowired DisasterService disasters;
    @Autowired ResourceService resources;
    @Autowired RequestService requests;
    @Autowired AllocationService allocations;
    @Autowired ShipmentService shipments;

    @Test void signedTokenWorkflowDispatchesOnceAndFieldAgentConfirmsActualDelivery() throws Exception {
        var scenario = scenario("20");
        var manager = member(scenario.organization(), Role.INVENTORY_MANAGER);
        var field = member(scenario.organization(), Role.FIELD_AGENT);
        var allocation = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("20"));
        String privateTracking = "Private-tracking-" + UUID.randomUUID();
        var created = json.readTree(call(manager, "POST", "/api/v1/shipments", Map.of(
                "destinationId", scenario.destination().getId(), "trackingReference", privateTracking,
                "allocationIds", List.of(allocation.getId()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.allocations[0].status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());
        stock(scenario, "50", "20");
        assertEquals(RequestStatus.FULFILLED, requests.find(scenario.requestId()).getStatus());
        call(manager, "POST", "/api/v1/shipments/" + id + "/dispatch", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED")).andExpect(jsonPath("$.dispatchedAt").isNotEmpty());
        stock(scenario, "30", "0");
        call(field, "POST", "/api/v1/shipments/" + id + "/deliver", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.allocations[0].status").value("DELIVERED"));
        stock(scenario, "30", "0");
        var request = requests.find(scenario.requestId());
        assertEquals(RequestStatus.DELIVERED, request.getStatus());
        assertAmount("20", request.getItems().getFirst().getDeliveredQuantity());
        call(field, "GET", "/api/v1/requests/" + request.getId(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deliveredQuantity").value(20));
        var shipment = shipments.get(id);
        assertFalse(shipment.deliveredAt().isBefore(shipment.dispatchedAt()));
        assertTrue(shipment.version() > created.get("version").asLong());
        var deliveryEvent = events.findAll().stream().filter(e -> e.getEntityId().equals(id)
                && e.getDetails().contains("DELIVERED")).findFirst().orElseThrow();
        assertEquals(field.getId(), deliveryEvent.getActor().getId());
        assertEquals("USER", deliveryEvent.getSource());
        assertFalse(events.findAll().stream().anyMatch(e -> e.getDetails().contains(privateTracking)));
        var admin = member(null, Role.SYSTEM_ADMIN);
        call(admin, "GET", "/api/v1/admin/audit-events?entityType=SHIPMENT&entityId=" + id, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test void cancellationReleasesAggregatedReservationsAndAllowsNewAllocation() {
        var scenario = scenario("20");
        var first = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("8"));
        var second = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("12"));
        var shipment = create(scenario, first, second);
        shipments.cancel(shipment.id());
        stock(scenario, "50", "0");
        var request = requests.find(scenario.requestId());
        assertAmount("0", request.getItems().getFirst().getAllocatedQuantity());
        assertEquals(RequestStatus.APPROVED, request.getStatus());
        assertEquals(ShipmentStatus.CANCELLED, shipments.get(shipment.id()).status());
        assertTrue(shipments.get(shipment.id()).allocations().stream().allMatch(a -> a.status() == AllocationStatus.CANCELLED));
        var replacement = allocations.allocate(scenario.requestId());
        assertEquals(1, replacement.size());
        assertFalse(Set.of(first.getId(), second.getId()).contains(replacement.getFirst().getId()));
        stock(scenario, "50", "20");
        assertEquals(RequestStatus.FULFILLED, requests.find(scenario.requestId()).getStatus());
    }

    @Test void partialDeliverySurvivesCancellationAndRemainingNeedCanBeDelivered() {
        var scenario = scenario("20");
        var first = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("8"));
        var second = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("12"));
        var firstShipment = create(scenario, first);
        var secondShipment = create(scenario, second);
        shipments.dispatch(firstShipment.id()); shipments.deliver(firstShipment.id());
        assertEquals(RequestStatus.PARTIALLY_DELIVERED, requests.find(scenario.requestId()).getStatus());
        shipments.cancel(secondShipment.id());
        stock(scenario, "42", "0");
        var item = requests.find(scenario.requestId()).getItems().getFirst();
        assertAmount("8", item.getAllocatedQuantity()); assertAmount("8", item.getDeliveredQuantity());
        var replacement = allocations.allocate(scenario.requestId()).getFirst();
        assertAmount("12", replacement.getQuantity());
        assertEquals(RequestStatus.PARTIALLY_DELIVERED, requests.find(scenario.requestId()).getStatus());
        var last = create(scenario, replacement);
        shipments.dispatch(last.id()); shipments.deliver(last.id());
        stock(scenario, "30", "0");
        assertEquals(RequestStatus.DELIVERED, requests.find(scenario.requestId()).getStatus());
        assertAmount("20", requests.find(scenario.requestId()).getItems().getFirst().getDeliveredQuantity());
    }

    @Test void invalidAndRepeatedTransitionsNeverChangeStockOrAppendSuccessEvents() throws Exception {
        var scenario = scenario("10");
        var manager = member(scenario.organization(), Role.DISASTER_COORDINATOR);
        var shipment = create(scenario, allocations.allocate(scenario.requestId()).getFirst());
        long initial = events.count();
        call(manager, "POST", "/api/v1/shipments/" + shipment.id() + "/deliver", null).andExpect(status().isUnprocessableEntity());
        assertEquals(initial, events.count()); stock(scenario, "50", "10");
        shipments.dispatch(shipment.id());
        long dispatched = events.count();
        for (String action : List.of("dispatch", "cancel")) call(manager, "POST",
                "/api/v1/shipments/" + shipment.id() + "/" + action, null).andExpect(status().isUnprocessableEntity());
        assertEquals(dispatched, events.count()); stock(scenario, "40", "0");
        shipments.deliver(shipment.id());
        long delivered = events.count();
        call(manager, "POST", "/api/v1/shipments/" + shipment.id() + "/deliver", null).andExpect(status().isUnprocessableEntity());
        assertEquals(delivered, events.count()); stock(scenario, "40", "0");
    }

    @Test void cancelledShipmentCannotBeDispatchedOrCancelledTwice() throws Exception {
        var scenario = scenario("10");
        var manager = member(scenario.organization(), Role.INVENTORY_MANAGER);
        var shipment = create(scenario, allocations.allocate(scenario.requestId()).getFirst());
        shipments.cancel(shipment.id());
        long count = events.count();
        for (String action : List.of("dispatch", "deliver", "cancel")) call(manager, "POST",
                "/api/v1/shipments/" + shipment.id() + "/" + action, null).andExpect(status().isUnprocessableEntity());
        assertEquals(count, events.count()); stock(scenario, "50", "0");
    }

    @Test void allocationCannotBeAssignedTwiceAndDestinationMustMatch() throws Exception {
        var scenario = scenario("10");
        var manager = member(scenario.organization(), Role.INVENTORY_MANAGER);
        var allocation = allocations.allocate(scenario.requestId()).getFirst();
        var wrong = locations.create("Other shelter", "Other address", scenario.organization().getId(), null, null);
        call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", wrong.getId(),
                "trackingReference", "wrong", "allocationIds", List.of(allocation.getId())))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, shipmentRepository.count());
        var shipment = create(scenario, allocation);
        long count = events.count();
        call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", scenario.destination().getId(),
                "trackingReference", "second", "allocationIds", List.of(allocation.getId())))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(1, shipmentRepository.count()); assertEquals(count, events.count());
        assertEquals(shipment.id(), allocationRepository.findById(allocation.getId()).orElseThrow().getShipment().getId());
    }

    @Test void crossOrganizationReadsWritesAndFiltersAreDeniedAndListsAreScoped() throws Exception {
        var own = scenario("10");
        var foreign = scenario("10");
        var manager = member(own.organization(), Role.INVENTORY_MANAGER);
        var ownShipment = create(own, allocations.allocate(own.requestId()).getFirst());
        var foreignAllocation = allocations.allocate(foreign.requestId()).getFirst();
        var foreignShipment = create(foreign, foreignAllocation);
        call(manager, "GET", "/api/v1/shipments", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownShipment.id().toString()));
        for (String action : List.of("dispatch", "deliver", "cancel")) call(manager, "POST",
                "/api/v1/shipments/" + foreignShipment.id() + "/" + action, null).andExpect(status().isForbidden());
        call(manager, "GET", "/api/v1/shipments/" + foreignShipment.id(), null).andExpect(status().isForbidden());
        call(manager, "GET", "/api/v1/shipments?organizationId=" + foreign.organization().getId(), null)
                .andExpect(status().isForbidden());
        call(manager, "GET", "/api/v1/shipments?destinationId=" + foreign.destination().getId(), null)
                .andExpect(status().isForbidden());
        call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", own.destination().getId(),
                "trackingReference", "cross-org", "allocationIds", List.of(foreignAllocation.getId())))
                .andExpect(status().isForbidden());
        var admin = member(null, Role.SYSTEM_ADMIN);
        call(admin, "GET", "/api/v1/shipments", null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        call(admin, "GET", "/api/v1/shipments?status=CREATED&destinationId=" + foreign.destination().getId(), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void roleMatrixAllowsReadsButRestrictsLogisticsAndDeliveryChanges() throws Exception {
        var scenario = scenario("10");
        var allocation = allocations.allocate(scenario.requestId()).getFirst();
        var shipment = create(scenario, allocation);
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/shipments")).andExpect(status().isUnauthorized());
        for (Role role : List.of(Role.VIEWER, Role.PARTNER_MANAGER, Role.FIELD_AGENT)) {
            var user = member(scenario.organization(), role);
            call(user, "GET", "/api/v1/shipments/" + shipment.id(), null).andExpect(status().isOk());
            call(user, "POST", "/api/v1/shipments", Map.of("destinationId", scenario.destination().getId(),
                    "trackingReference", "forbidden", "allocationIds", List.of(allocation.getId())))
                    .andExpect(status().isForbidden());
            for (String action : List.of("dispatch", "cancel")) call(user, "POST",
                    "/api/v1/shipments/" + shipment.id() + "/" + action, null).andExpect(status().isForbidden());
            if (role != Role.FIELD_AGENT) call(user, "POST", "/api/v1/shipments/" + shipment.id() + "/deliver", null)
                    .andExpect(status().isForbidden());
        }
        stock(scenario, "50", "10");
    }

    @Test void malformedManifestsAndPaginationAreRejectedAndUnknownShipmentIsNotFound() throws Exception {
        var scenario = scenario("10");
        var manager = member(scenario.organization(), Role.INVENTORY_MANAGER);
        var allocation = allocations.allocate(scenario.requestId()).getFirst();
        for (Object ids : List.of(List.of(), Collections.nCopies(101, allocation.getId()))) {
            call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", scenario.destination().getId(),
                    "trackingReference", "bad", "allocationIds", ids)).andExpect(status().isBadRequest());
        }
        call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", scenario.destination().getId(),
                "trackingReference", "duplicate", "allocationIds", List.of(allocation.getId(), allocation.getId())))
                .andExpect(status().isUnprocessableEntity());
        call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", scenario.destination().getId(),
                "trackingReference", " ", "allocationIds", List.of(allocation.getId()))).andExpect(status().isBadRequest());
        for (String query : List.of("page=-1", "page=100001", "size=0", "size=101", "status=bad", "destinationId=bad"))
            call(manager, "GET", "/api/v1/shipments?" + query, null).andExpect(status().isBadRequest());
        call(manager, "GET", "/api/v1/shipments/" + UUID.randomUUID(), null).andExpect(status().isNotFound());
        assertEquals(0, shipmentRepository.count());
    }

    @Test void duplicateTrackingReferenceConflictsWithoutConsumingAnotherAllocation() throws Exception {
        var scenario = scenario("20");
        var manager = member(scenario.organization(), Role.INVENTORY_MANAGER);
        var first = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("10"));
        var second = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("10"));
        var shipment = create(scenario, first);
        call(manager, "POST", "/api/v1/shipments", Map.of("destinationId", scenario.destination().getId(),
                "trackingReference", shipment.trackingReference(), "allocationIds", List.of(second.getId())))
                .andExpect(status().isConflict());
        assertNull(allocationRepository.findById(second.getId()).orElseThrow().getShipment());
        assertEquals(1, shipmentRepository.count());
    }

    @Test void multipleRequestsAndStockLocationsCanShareOneDestinationManifest() {
        var scenario = scenario("10");
        var otherLocation = locations.create("Depot", "Depot address", scenario.organization().getId(), null, null);
        var otherStock = resources.receive(otherLocation.getId(), scenario.resourceId(), amount("15"));
        var other = requests.create("OTHER-" + UUID.randomUUID().toString().substring(0,8),
                scenario.disasterId(), scenario.requesterId(), scenario.destination().getId(), RequestPriority.HIGH, null);
        requests.addItem(other.getId(), scenario.resourceId(), amount("15"));
        requests.submit(other.getId()); requests.approve(other.getId());
        var first = allocations.reserve(scenario.itemId(), scenario.inventory().getId(), amount("10"));
        var second = allocations.reserve(other.getItems().getFirst().getId(), otherStock.getId(), amount("15"));
        var shipment = create(scenario, first, second);
        shipments.dispatch(shipment.id()); shipments.deliver(shipment.id());
        stock(scenario, "40", "0");
        assertAmount("0", resources.findInventory(otherStock.getId()).getQuantity());
        assertEquals(RequestStatus.DELIVERED, requests.find(other.getId()).getStatus());
        assertEquals(RequestStatus.DELIVERED, requests.find(scenario.requestId()).getStatus());
    }

    private record Scenario(Organization organization, Location destination, Inventory inventory,
            UUID requestId, UUID itemId, UUID resourceId, UUID disasterId, UUID requesterId) {}
    private Scenario scenario(String quantity) {
        var organization = new Organization(); organization.setName("Shipment organization");
        organization = organizations.saveAndFlush(organization);
        var user = member(organization, Role.FIELD_AGENT);
        var destination = locations.create("Shelter", "Address", organization.getId(), null, null);
        var disaster = disasters.createForOrganization("Flood", DisasterType.FLOOD, null, Instant.now(), organization.getId());
        disasters.activate(disaster.getId()); disasters.addAffectedLocation(disaster.getId(), destination.getId());
        var resource = resources.createResourceType("Water-" + UUID.randomUUID(), "litres", false);
        var inventory = resources.receive(destination.getId(), resource.getId(), amount("50"));
        var request = requests.create("SHIP-" + UUID.randomUUID().toString().substring(0,12),
                disaster.getId(), user.getId(), destination.getId(), RequestPriority.HIGH, null);
        requests.addItem(request.getId(), resource.getId(), amount(quantity));
        requests.submit(request.getId()); requests.approve(request.getId());
        return new Scenario(organization, destination, inventory, request.getId(), request.getItems().getFirst().getId(),
                resource.getId(), disaster.getId(), user.getId());
    }
    private ShipmentDtos.View create(Scenario scenario, Allocation... manifest) {
        return shipments.create(new ShipmentDtos.Create(scenario.destination().getId(), "TRACK-" + UUID.randomUUID(),
                Arrays.stream(manifest).map(Allocation::getId).toList()));
    }
    private UserProfile member(Organization organization, Role role) {
        var user = new UserProfile(); user.setDisplayName("Shipment user");
        user.setExternalIssuer(TestIdentityProvider.ISSUER); user.setExternalSubject("subject-" + UUID.randomUUID());
        user.setOrganization(organization); user.setRoles(new HashSet<>(Set.of(role)));
        return users.saveAndFlush(user);
    }
    private ResultActions call(UserProfile user, String method, String path, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.token(user.getExternalSubject()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private static void assertAmount(String expected, BigDecimal actual) { assertEquals(0, amount(expected).compareTo(actual)); }
    private void stock(Scenario scenario, String quantity, String reserved) {
        var inventory = resources.findInventory(scenario.inventory().getId());
        assertAmount(quantity, inventory.getQuantity()); assertAmount(reserved, inventory.getReservedQuantity());
    }
}
