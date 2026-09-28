package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.ApiResponses.*;
import com.disasterrelief.allocator.domain.*;

public final class ApiMapper {
    private ApiMapper() { }
    public static DisasterResponse disaster(Disaster value) {
        return new DisasterResponse(value.getId(), value.getName(), value.getType(), value.getStatus(),
                value.getDescription(), value.getStartedAt(), value.getEndedAt(), value.getAffectedLocations()
                        .stream().map(Location::getId).collect(java.util.stream.Collectors.toSet()));
    }
    public static LocationResponse location(Location value) {
        return new LocationResponse(value.getId(), value.getName(), value.getAddress(), value.getRegion(),
                value.getLatitude(), value.getLongitude(), value.getOrganization().getId());
    }
    public static ResourceTypeResponse resourceType(ResourceType value) {
        return new ResourceTypeResponse(value.getId(), value.getName(), value.getUnitOfMeasure(),
                value.isPerishable(), value.isActive());
    }
    public static InventoryResponse inventory(Inventory value) {
        return new InventoryResponse(value.getId(), value.getLocation().getId(), value.getResourceType().getId(),
                value.getQuantity(), value.getReservedQuantity(), value.getQuantity().subtract(value.getReservedQuantity()));
    }
    public static RequestResponse request(ResourceRequest value) {
        return new RequestResponse(value.getId(), value.getReference(), value.getDisaster().getId(),
                value.getRequestedBy().getId(), value.getDestination().getId(), value.getStatus(), value.getPriority(),
                value.getNotes(), value.getItems().stream().map(item -> new RequestItemResponse(item.getId(),
                        item.getResourceType().getId(), item.getRequestedQuantity(), item.getAllocatedQuantity())).toList());
    }
    public static AllocationResponse allocation(Allocation value) {
        return new AllocationResponse(value.getId(), value.getRequestItem().getId(), value.getInventory().getId(),
                value.getQuantity(), value.getStatus(), value.getShipment() == null ? null : value.getShipment().getId());
    }
}