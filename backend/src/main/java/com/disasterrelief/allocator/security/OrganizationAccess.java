package com.disasterrelief.allocator.security;

import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationAccess {
    private final CurrentActor actor;
    private final OrganizationRepository organizations;
    private final LocationRepository locations;
    private final DisasterRepository disasters;
    private final ResourceRequestRepository requests;
    private final ResourceRequestItemRepository items;
    private final InventoryRepository inventories;
    private final UserProfileRepository users;
    private final ShipmentRepository shipments;
    private final AllocationRepository allocations;

    public UUID organizationForCreate(UUID requested) {
        var identity = actor.current();
        UUID id = requested == null ? identity.organizationId() : requested;
        if (id == null) throw new AccessDeniedException("An organization must be selected");
        organization(id);
        return id;
    }

    public void organization(UUID id) {
        var identity = actor.current();
        if (id == null || (!identity.isAdministrator() && !id.equals(identity.organizationId()))) denied();
        var organization = organizations.findById(id).orElseThrow(() -> missing());
        if (!organization.isActive()) denied();
    }

    public void location(UUID id) { organization(locationEntity(id).getOrganization().getId()); }

    public void disaster(UUID id) {
        var value = disasters.findById(id).orElseThrow(() -> missing());
        var identity = actor.current();
        if (value.getOrganization() == null) {
            if (!identity.isAdministrator()) denied();
        } else organization(value.getOrganization().getId());
    }

    public void affectedLocation(UUID disasterId, UUID locationId) {
        disaster(disasterId);
        location(locationId);
        var value = disasters.findById(disasterId).orElseThrow(() -> missing());
        if (value.getOrganization() != null && !value.getOrganization().getId()
                .equals(locationEntity(locationId).getOrganization().getId())) denied();
    }

    public void request(UUID id) {
        var value = requests.findById(id).orElseThrow(() -> missing());
        organization(value.getDestination().getOrganization().getId());
    }

    public void editRequest(UUID id) {
        request(id);
        var identity = actor.current();
        var value = requests.findById(id).orElseThrow(() -> missing());
        if (!identity.isAdministrator() && !identity.roles().contains(Role.PARTNER_MANAGER)
                && !identity.profileId().equals(value.getRequestedBy().getId())) denied();
    }

    public void createRequest(UUID requesterId, UUID destinationId, UUID disasterId) {
        var identity = actor.current();
        location(destinationId);
        disaster(disasterId);
        var user = users.findById(requesterId).orElseThrow(() -> missing());
        UUID org = locationEntity(destinationId).getOrganization().getId();
        if (!user.isActive() || user.getOrganization() == null
                || !org.equals(user.getOrganization().getId())
                || (!identity.isAdministrator() && !identity.profileId().equals(requesterId))) denied();
        var disaster = disasters.findById(disasterId).orElseThrow(() -> missing());
        if (disaster.getOrganization() != null && !org.equals(disaster.getOrganization().getId())) denied();
    }

    public void inventory(UUID id) {
        organization(inventories.findById(id).orElseThrow(() -> missing()).getLocation().getOrganization().getId());
    }

    public UUID inventoryScope() {
        var identity = actor.current();
        return identity.isAdministrator() ? null : identity.organizationId();
    }

    public boolean canReadInventory(Inventory inventory) {
        var identity = actor.current();
        return identity.isAdministrator() || inventory.getLocation().getOrganization().getId()
                .equals(identity.organizationId());
    }

    public void requestItem(UUID id) {
        var item = items.findById(id).orElseThrow(() -> missing());
        request(item.getRequest().getId());
    }

    public void reservation(UUID itemId, UUID inventoryId) {
        requestItem(itemId);
        inventory(inventoryId);
        var item = items.findById(itemId).orElseThrow(() -> missing());
        var stock = inventories.findById(inventoryId).orElseThrow(() -> missing());
        if (!item.getRequest().getDestination().getOrganization().getId()
                .equals(stock.getLocation().getOrganization().getId())) denied();
    }

    public void shipment(UUID id) {
        var shipment = shipments.findById(id).orElseThrow(() -> missing());
        organization(shipment.getDestination().getOrganization().getId());
    }

    public void allocation(UUID id) {
        var allocation = allocations.findById(id).orElseThrow(() -> missing());
        organization(allocation.getInventory().getLocation().getOrganization().getId());
        request(allocation.getRequestItem().getRequest().getId());
    }

    private Location locationEntity(UUID id) { return locations.findById(id).orElseThrow(() -> missing()); }
    private ResourceNotFoundException missing() { return new ResourceNotFoundException("Resource not found"); }
    private void denied() { throw new AccessDeniedException("Organization access denied"); }
}
