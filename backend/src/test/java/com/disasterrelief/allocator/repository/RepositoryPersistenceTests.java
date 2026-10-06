package com.disasterrelief.allocator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterStatus;
import com.disasterrelief.allocator.domain.DisasterType;
import com.disasterrelief.allocator.domain.Inventory;
import com.disasterrelief.allocator.domain.Location;
import com.disasterrelief.allocator.domain.Organization;
import com.disasterrelief.allocator.domain.RequestPriority;
import com.disasterrelief.allocator.domain.RequestStatus;
import com.disasterrelief.allocator.domain.ResourceRequest;
import com.disasterrelief.allocator.domain.ResourceType;
import com.disasterrelief.allocator.domain.UserProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RepositoryPersistenceTests {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private ResourceTypeRepository resourceTypeRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DisasterRepository disasterRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ResourceRequestRepository resourceRequestRepository;

    @Test
    void findsInventoryWithAvailableQuantity() {
        Organization organization = organizationRepository.save(organization("Relief Org"));
        Location warehouse = locationRepository.save(location(organization, "Central Warehouse"));
        ResourceType water = resourceTypeRepository.save(resourceType("Drinking Water"));

        Inventory available = inventory(warehouse, water, "100.000", "25.000");
        Inventory fullyReserved = inventory(warehouse, resourceTypeRepository.save(resourceType("Blankets")), "10.000", "10.000");
        List<Inventory> savedInventory = inventoryRepository.saveAll(List.of(available, fullyReserved));
        available = savedInventory.get(0);

        assertThat(inventoryRepository.findAvailableByResourceTypeId(water.getId()))
            .extracting(Inventory::getId)
            .containsExactly(available.getId());
        assertThat(inventoryRepository.findByLocationIdAndResourceTypeId(warehouse.getId(), water.getId()))
            .get()
            .extracting(Inventory::getId)
            .isEqualTo(available.getId());
    }

    @Test
    void findsRequestsByDisasterAndStatus() {
        Organization organization = organizationRepository.save(organization("Field Partner"));
        Location destination = locationRepository.save(location(organization, "Shelter"));
        UserProfile requester = new UserProfile();
        requester.setExternalSubject("field-agent-1");
        requester.setDisplayName("Field Agent");
        requester.setOrganization(organization);
        requester = userProfileRepository.save(requester);

        Disaster disaster = new Disaster();
        disaster.setName("River Flood");
        disaster.setType(DisasterType.FLOOD);
        disaster.setStatus(DisasterStatus.ACTIVE);
        disaster.setStartedAt(Instant.now());
        disaster.getAffectedLocations().add(destination);
        disaster = disasterRepository.save(disaster);

        ResourceRequest request = new ResourceRequest();
        request.setReference("REQ-1001");
        request.setDisaster(disaster);
        request.setRequestedBy(requester);
        request.setDestination(destination);
        request.setStatus(RequestStatus.SUBMITTED);
        request.setPriority(RequestPriority.HIGH);
        resourceRequestRepository.save(request);

        assertThat(resourceRequestRepository.findByReference("REQ-1001"))
            .get()
            .extracting(ResourceRequest::getId)
            .isEqualTo(request.getId());
        assertThat(resourceRequestRepository.findByDisasterIdAndStatus(disaster.getId(), RequestStatus.SUBMITTED))
            .extracting(ResourceRequest::getId)
            .containsExactly(request.getId());
        assertThat(disasterRepository.findByAffectedLocationsId(destination.getId()))
            .extracting(Disaster::getId)
            .containsExactly(disaster.getId());
    }

    private Organization organization(String name) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setCode(name.replace(" ", "-").toUpperCase());
        return organization;
    }

    private Location location(Organization organization, String name) {
        Location location = new Location();
        location.setName(name);
        location.setAddress("1 Relief Way");
        location.setOrganization(organization);
        return location;
    }

    private ResourceType resourceType(String name) {
        ResourceType resourceType = new ResourceType();
        resourceType.setName(name);
        resourceType.setUnitOfMeasure("units");
        return resourceType;
    }

    private Inventory inventory(Location location, ResourceType resourceType, String quantity, String reservedQuantity) {
        Inventory inventory = new Inventory();
        inventory.setLocation(location);
        inventory.setResourceType(resourceType);
        inventory.setQuantity(new BigDecimal(quantity));
        inventory.setReservedQuantity(new BigDecimal(reservedQuantity));
        return inventory;
    }
}
