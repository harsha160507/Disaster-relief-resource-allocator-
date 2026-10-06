package com.disasterrelief.allocator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.disasterrelief.allocator.domain.Allocation;
import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterStatus;
import com.disasterrelief.allocator.domain.DisasterType;
import com.disasterrelief.allocator.domain.Inventory;
import com.disasterrelief.allocator.domain.Location;
import com.disasterrelief.allocator.domain.Organization;
import com.disasterrelief.allocator.domain.RequestPriority;
import com.disasterrelief.allocator.domain.RequestStatus;
import com.disasterrelief.allocator.domain.ResourceRequest;
import com.disasterrelief.allocator.domain.ResourceRequestItem;
import com.disasterrelief.allocator.domain.ResourceType;
import com.disasterrelief.allocator.domain.UserProfile;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.repository.AllocationRepository;
import com.disasterrelief.allocator.repository.DisasterRepository;
import com.disasterrelief.allocator.repository.InventoryRepository;
import com.disasterrelief.allocator.repository.OrganizationRepository;
import com.disasterrelief.allocator.repository.ResourceRequestItemRepository;
import com.disasterrelief.allocator.repository.ResourceRequestRepository;
import com.disasterrelief.allocator.repository.ResourceTypeRepository;
import com.disasterrelief.allocator.repository.UserProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({DisasterService.class, LocationService.class, ResourceService.class,
        RequestService.class, AllocationService.class})
class ServiceBusinessRuleTests {

    @Autowired
    private DisasterService disasterService;

    @Autowired
    private LocationService locationService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private DisasterRepository disasterRepository;

    @Autowired
    private ResourceTypeRepository resourceTypeRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ResourceRequestRepository requestRepository;

    @Autowired
    private ResourceRequestItemRepository requestItemRepository;

    private Organization organization;
    private Location destination;
    private UserProfile requester;
    private Disaster disaster;
    private ResourceType water;
        private ResourceType blankets;

        @Autowired
        private AllocationRepository allocationRepository;

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setName("Relief Organization");
        organization = organizationRepository.save(organization);
        destination = locationService.create("Shelter", "1 Relief Way", organization.getId(), null, null);

        requester = new UserProfile();
        requester.setExternalSubject("field-agent");
        requester.setDisplayName("Field Agent");
        requester.setOrganization(organization);
        requester = userProfileRepository.save(requester);

        disaster = disasterService.create("River Flood", DisasterType.FLOOD, null, Instant.now());
        disasterService.activate(disaster.getId());
        disaster = disasterService.addAffectedLocation(disaster.getId(), destination.getId());
        water = resourceService.createResourceType("Drinking Water", "litres", false);
        blankets = resourceService.createResourceType("Blankets", "items", false);
    }

    @Test
    void cannotCreateRequestForUnaffectedLocation() {
        Location otherLocation = locationService.create("Other Shelter", "2 Relief Way", organization.getId(), null, null);

        assertThatThrownBy(() -> requestService.create("REQ-1", disaster.getId(), requester.getId(),
                otherLocation.getId(), RequestPriority.HIGH, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not affected");
    }

    @Test
    void allocationReservesInventoryAndRejectsInsufficientQuantity() {
        ResourceRequest request = requestService.create("REQ-2", disaster.getId(), requester.getId(),
                destination.getId(), RequestPriority.CRITICAL, null);
        ResourceRequestItem item = requestService.addItem(request.getId(), water.getId(), new BigDecimal("120"))
                .getItems().get(0);
        requestService.submit(request.getId());
        requestService.approve(request.getId());
        Inventory inventory = resourceService.receive(destination.getId(), water.getId(), new BigDecimal("100"));

        Allocation allocation = allocationService.reserve(item.getId(), inventory.getId(), new BigDecimal("60"));
        Inventory updatedInventory = inventoryRepository.findById(inventory.getId()).orElseThrow();
        ResourceRequestItem updatedItem = requestItemRepository.findById(item.getId()).orElseThrow();

        assertThat(allocation.getQuantity()).isEqualByComparingTo("60");
        assertThat(updatedInventory.getReservedQuantity()).isEqualByComparingTo("60");
        assertThat(updatedItem.getAllocatedQuantity()).isEqualByComparingTo("60");

        assertThatThrownBy(() -> allocationService.reserve(item.getId(), inventory.getId(), new BigDecimal("50")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Insufficient available inventory");
        assertThat(inventoryRepository.findById(inventory.getId()).orElseThrow().getReservedQuantity())
                .isEqualByComparingTo("60");
        assertThat(requestItemRepository.findById(item.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("60");
        assertThat(allocationRepository.findByRequestItemId(item.getId())).hasSize(1);
    }

    @Test
    void requestCannotBeSubmittedWithoutItems() {
        ResourceRequest request = requestService.create("REQ-3", disaster.getId(), requester.getId(),
                destination.getId(), RequestPriority.NORMAL, null);

        assertThatThrownBy(() -> requestService.submit(request.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("at least one resource item");
    }

    @Test
    void automaticallyAllocatesAcrossInventoriesAndFulfillsRequest() {
        Location secondWarehouse = locationService.create("Warehouse", "3 Relief Way", organization.getId(), null, null);
        ResourceRequest request = createRequest("REQ-AUTO-1");
        ResourceRequestItem item = requestService.addItem(request.getId(), water.getId(), new BigDecimal("120"))
                .getItems().get(0);
        approve(request);
        Inventory first = resourceService.receive(destination.getId(), water.getId(), new BigDecimal("100"));
        Inventory second = resourceService.receive(secondWarehouse.getId(), water.getId(), new BigDecimal("50"));

        java.util.List<Allocation> allocations = allocationService.allocate(request.getId());

        assertThat(allocations).hasSize(2);
        assertThat(requestItemRepository.findById(item.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("120");
        BigDecimal firstReserved = inventoryRepository.findById(first.getId()).orElseThrow().getReservedQuantity();
        BigDecimal secondReserved = inventoryRepository.findById(second.getId()).orElseThrow().getReservedQuantity();
        assertThat(firstReserved.add(secondReserved)).isEqualByComparingTo("120");
        assertThat(firstReserved).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(secondReserved).isBetween(BigDecimal.ZERO, new BigDecimal("50"));
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.FULFILLED);
        assertThat(allocationRepository.findByRequestItemId(item.getId())).hasSize(2);
    }

    @Test
    void automaticAllocationLeavesRequestPartiallyFulfilledWhenSupplyIsInsufficient() {
        ResourceRequest request = createRequest("REQ-AUTO-2");
        ResourceRequestItem item = requestService.addItem(request.getId(), water.getId(), new BigDecimal("200"))
                .getItems().get(0);
        approve(request);
        Inventory inventory = resourceService.receive(destination.getId(), water.getId(), new BigDecimal("75"));

        java.util.List<Allocation> allocations = allocationService.allocate(request.getId());

        assertThat(allocations).hasSize(1);
        assertThat(requestItemRepository.findById(item.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("75");
        assertThat(inventoryRepository.findById(inventory.getId()).orElseThrow().getReservedQuantity())
                .isEqualByComparingTo("75");
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void repeatedAllocationFulfillsRemainingNeedWithoutOverAllocating() {
        ResourceRequest request = createRequest("REQ-AUTO-3");
        ResourceRequestItem item = requestService.addItem(request.getId(), water.getId(), new BigDecimal("100"))
                .getItems().get(0);
        approve(request);
        resourceService.receive(destination.getId(), water.getId(), new BigDecimal("40"));

        allocationService.allocate(request.getId());
        resourceService.receive(destination.getId(), water.getId(), new BigDecimal("80"));
        allocationService.allocate(request.getId());

        assertThat(requestItemRepository.findById(item.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("100");
        assertThat(allocationRepository.findByRequestItemId(item.getId())).hasSize(2);
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.FULFILLED);
    }

    @Test
    void automaticAllocationHandlesMultipleResourceItems() {
        ResourceRequest request = createRequest("REQ-AUTO-4");
        ResourceRequestItem waterItem = requestService.addItem(request.getId(), water.getId(), new BigDecimal("10"))
                .getItems().get(0);
        ResourceRequestItem blanketItem = requestService.addItem(request.getId(), blankets.getId(), new BigDecimal("4"))
                .getItems().get(1);
        approve(request);
        resourceService.receive(destination.getId(), water.getId(), new BigDecimal("10"));
        resourceService.receive(destination.getId(), blankets.getId(), new BigDecimal("4"));

        java.util.List<Allocation> allocations = allocationService.allocate(request.getId());

        assertThat(allocations).hasSize(2);
        assertThat(requestItemRepository.findById(waterItem.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("10");
        assertThat(requestItemRepository.findById(blanketItem.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("4");
        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.FULFILLED);
    }

    @Test
    void allocationRejectsUnapprovedRequestWithoutChangingInventory() {
        ResourceRequest request = requestService.create("REQ-AUTO-5", disaster.getId(), requester.getId(),
                destination.getId(), RequestPriority.NORMAL, null);
        ResourceRequestItem item = requestService.addItem(request.getId(), water.getId(), new BigDecimal("5"))
                .getItems().get(0);
        Inventory inventory = resourceService.receive(destination.getId(), water.getId(), new BigDecimal("5"));

        assertThatThrownBy(() -> allocationService.allocate(request.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Only approved");
        assertThat(inventoryRepository.findById(inventory.getId()).orElseThrow().getReservedQuantity())
                .isEqualByComparingTo("0");
        assertThat(requestItemRepository.findById(item.getId()).orElseThrow().getAllocatedQuantity())
                .isEqualByComparingTo("0");
        assertThat(allocationRepository.findByRequestItemId(item.getId())).isEmpty();
    }

        private ResourceRequest createRequest(String reference) {
                return requestService.create(reference, disaster.getId(), requester.getId(),
                                destination.getId(), RequestPriority.HIGH, null);
        }

        private void approve(ResourceRequest request) {
                requestService.submit(request.getId());
                requestService.approve(request.getId());
    }
}
