package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.api.dto.ShipmentDtos;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.*;
import com.disasterrelief.allocator.exception.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final AllocationRepository allocations;
    private final ResourceRequestRepository requests;
    private final InventoryRepository inventories;
    private final LocationRepository locations;
    private final AuditWriter audit;

    @Transactional
    public ShipmentDtos.View create(ShipmentDtos.Create input) {
        var ids = input.allocationIds();
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.contains(null)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessRuleViolationException("A manifest requires 1 to 100 distinct allocation identifiers");
        }
        String tracking = input.trackingReference() == null ? "" : input.trackingReference().trim();
        if (tracking.isBlank() || tracking.length() > 255) throw new BusinessRuleViolationException("A tracking reference is required");
        if (shipments.findByTrackingReference(tracking).isPresent()) throw new ResourceConflictException("Tracking reference already exists");
        var destination = locations.findById(input.destinationId()).orElseThrow(() -> missing("Destination"));
        if (!destination.getOrganization().isActive()) throw new BusinessRuleViolationException("Organization is inactive");
        lockRequests(ids);
        var manifest = lockAllocations(ids);
        for (var allocation : manifest) {
            validateDestination(allocation, destination);
            if (allocation.getStatus() != AllocationStatus.RESERVED || allocation.getShipment() != null) {
                throw new BusinessRuleViolationException("Only unassigned reserved allocations can enter a shipment");
            }
        }
        var shipment = new Shipment();
        shipment.setDestination(destination);
        shipment.setTrackingReference(tracking);
        shipment = shipments.saveAndFlush(shipment);
        for (var allocation : manifest) {
            allocation.setShipment(shipment);
            shipment.getAllocations().add(allocation);
            allocationStatus(allocation, AllocationStatus.CONFIRMED);
        }
        audit.record(AuditAction.CREATED, "SHIPMENT", shipment.getId(), owner(shipment),
                "shipment.created", Map.of("status", shipment.getStatus(), "destinationId", destination.getId(),
                        "allocationCount", manifest.size()));
        return ShipmentDtos.view(shipment);
    }

    @Transactional
    public ShipmentDtos.View dispatch(UUID id) {
        var manifest = locked(id, ShipmentStatus.CREATED);
        var shipment = manifest.shipment();
        var stock = lockStock(manifest.allocations());
        var totals = totals(manifest.allocations(), true);
        for (var entry : totals.entrySet()) {
            var inventory = stock.get(entry.getKey());
            if (inventory.getReservedQuantity().compareTo(entry.getValue()) < 0
                    || inventory.getQuantity().compareTo(entry.getValue()) < 0) {
                throw new BusinessRuleViolationException("Shipment exceeds reserved or physical stock");
            }
        }
        for (var entry : totals.entrySet()) {
            var inventory = stock.get(entry.getKey());
            BigDecimal before = inventory.getQuantity(), reserved = inventory.getReservedQuantity();
            inventory.setQuantity(before.subtract(entry.getValue()));
            inventory.setReservedQuantity(reserved.subtract(entry.getValue()));
            stockEvent(inventory, before, reserved, shipment, "inventory.dispatched");
        }
        for (var allocation : manifest.allocations()) allocationStatus(allocation, AllocationStatus.DISPATCHED);
        shipment.setDispatchedAt(Instant.now());
        shipmentStatus(shipment, ShipmentStatus.DISPATCHED);
        shipments.flush();
        return ShipmentDtos.view(shipment);
    }

    @Transactional
    public ShipmentDtos.View deliver(UUID id) {
        var manifest = locked(id, ShipmentStatus.DISPATCHED);
        var shipment = manifest.shipment();
        Instant deliveredAt = Instant.now();
        if (deliveredAt.isBefore(shipment.getDispatchedAt())) {
            throw new BusinessRuleViolationException("Delivery cannot precede dispatch");
        }
        var itemTotals = totals(manifest.allocations(), false);
        for (var allocation : manifest.allocations()) {
            var item = allocation.getRequestItem();
            if (item.getDeliveredQuantity().add(itemTotals.get(item.getId())).compareTo(item.getAllocatedQuantity()) > 0) {
                throw new BusinessRuleViolationException("Delivery exceeds the item's allocated quantity");
            }
        }
        for (var allocation : manifest.allocations()) {
            var item = allocation.getRequestItem();
            BigDecimal before = item.getDeliveredQuantity();
            item.setDeliveredQuantity(before.add(allocation.getQuantity()));
            audit.record(AuditAction.UPDATED, "RESOURCE_REQUEST_ITEM", item.getId(), owner(shipment),
                    "request_item.delivered", Map.of("shipmentId", id, "requestId", item.getRequest().getId(),
                            "beforeDeliveredQuantity", before, "afterDeliveredQuantity", item.getDeliveredQuantity()));
            allocationStatus(allocation, AllocationStatus.DELIVERED);
        }
        shipment.setDeliveredAt(deliveredAt);
        shipmentStatus(shipment, ShipmentStatus.DELIVERED);
        manifest.requests().forEach(request -> RequestProgress.update(request, audit));
        shipments.flush();
        return ShipmentDtos.view(shipment);
    }

    @Transactional
    public ShipmentDtos.View cancel(UUID id) {
        var manifest = locked(id, ShipmentStatus.CREATED);
        var shipment = manifest.shipment();
        var stock = lockStock(manifest.allocations());
        var stockTotals = totals(manifest.allocations(), true);
        var itemTotals = totals(manifest.allocations(), false);
        for (var entry : stockTotals.entrySet()) {
            if (stock.get(entry.getKey()).getReservedQuantity().compareTo(entry.getValue()) < 0) {
                throw new BusinessRuleViolationException("Shipment exceeds reserved stock");
            }
        }
        for (var allocation : manifest.allocations()) {
            var item = allocation.getRequestItem();
            if (item.getAllocatedQuantity().subtract(itemTotals.get(item.getId())).compareTo(item.getDeliveredQuantity()) < 0) {
                throw new BusinessRuleViolationException("Cancellation would release delivered goods");
            }
        }
        for (var entry : stockTotals.entrySet()) {
            var inventory = stock.get(entry.getKey());
            BigDecimal reserved = inventory.getReservedQuantity();
            inventory.setReservedQuantity(reserved.subtract(entry.getValue()));
            stockEvent(inventory, inventory.getQuantity(), reserved, shipment, "inventory.reservation_released");
        }
        for (var allocation : manifest.allocations()) {
            var item = allocation.getRequestItem();
            BigDecimal before = item.getAllocatedQuantity();
            item.setAllocatedQuantity(before.subtract(allocation.getQuantity()));
            audit.record(AuditAction.UPDATED, "RESOURCE_REQUEST_ITEM", item.getId(), owner(shipment),
                    "request_item.reservation_released", Map.of("shipmentId", id, "requestId", item.getRequest().getId(),
                            "beforeAllocatedQuantity", before, "afterAllocatedQuantity", item.getAllocatedQuantity()));
            allocationStatus(allocation, AllocationStatus.CANCELLED);
        }
        shipmentStatus(shipment, ShipmentStatus.CANCELLED);
        manifest.requests().forEach(request -> RequestProgress.update(request, audit));
        shipments.flush();
        return ShipmentDtos.view(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentDtos.View get(UUID id) {
        return ShipmentDtos.view(shipments.findById(id).orElseThrow(() -> missing("Shipment")));
    }

    @Transactional(readOnly = true)
    public ShipmentDtos.PageView search(int page, int size, UUID organizationId, UUID destinationId, ShipmentStatus status) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page bounds");
        Specification<Shipment> filter = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (organizationId != null) predicates.add(cb.equal(root.get("destination").get("organization").get("id"), organizationId));
            if (destinationId != null) predicates.add(cb.equal(root.get("destination").get("id"), destinationId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = shipments.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new ShipmentDtos.PageView(result.getContent().stream().map(ShipmentDtos::summary).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private record Manifest(Shipment shipment, List<Allocation> allocations, List<ResourceRequest> requests) {}

    private Manifest locked(UUID id, ShipmentStatus expected) {
        var shipment = shipments.findByIdForUpdate(id).orElseThrow(() -> missing("Shipment"));
        if (shipment.getStatus() != expected) throw new BusinessRuleViolationException("Shipment must be " + expected);
        if ((expected == ShipmentStatus.CREATED && (shipment.getDispatchedAt() != null || shipment.getDeliveredAt() != null))
                || (expected == ShipmentStatus.DISPATCHED && (shipment.getDispatchedAt() == null || shipment.getDeliveredAt() != null))) {
            throw new BusinessRuleViolationException("Shipment timestamps are inconsistent");
        }
        var ids = allocations.idsForShipment(id);
        if (ids.isEmpty() || ids.size() > 100) throw new BusinessRuleViolationException("Shipment manifest is empty or exceeds 100 allocations");
        var lockedRequests = lockRequests(ids);
        var manifest = lockAllocations(ids);
        var allocationStatus = expected == ShipmentStatus.CREATED ? AllocationStatus.CONFIRMED : AllocationStatus.DISPATCHED;
        for (var allocation : manifest) {
            validateDestination(allocation, shipment.getDestination());
            if (allocation.getShipment() == null || !id.equals(allocation.getShipment().getId())
                    || allocation.getStatus() != allocationStatus) {
                throw new BusinessRuleViolationException("Shipment manifest contains an incompatible allocation");
            }
        }
        return new Manifest(shipment, manifest, lockedRequests);
    }

    private List<ResourceRequest> lockRequests(List<UUID> allocationIds) {
        var requestIds = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
        for (var id : allocationIds) requestIds.add(allocations.requestIdForAllocation(id).orElseThrow(() -> missing("Allocation")));
        return requestIds.stream().map(id -> requests.findByIdForUpdate(id).orElseThrow(() -> missing("Request"))).toList();
    }

    private List<Allocation> lockAllocations(List<UUID> ids) {
        return ids.stream().sorted(Comparator.comparing(UUID::toString))
                .map(id -> allocations.findByIdForUpdate(id).orElseThrow(() -> missing("Allocation"))).toList();
    }

    private Map<UUID, Inventory> lockStock(List<Allocation> manifest) {
        var result = new LinkedHashMap<UUID, Inventory>();
        manifest.stream().map(a -> a.getInventory().getId()).distinct().sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> result.put(id, inventories.findByIdForUpdate(id).orElseThrow(() -> missing("Inventory"))));
        return result;
    }

    private Map<UUID, BigDecimal> totals(List<Allocation> manifest, boolean inventory) {
        var result = new LinkedHashMap<UUID, BigDecimal>();
        for (var allocation : manifest) {
            UUID id = inventory ? allocation.getInventory().getId() : allocation.getRequestItem().getId();
            result.merge(id, allocation.getQuantity(), BigDecimal::add);
        }
        return result;
    }

    private void validateDestination(Allocation allocation, Location destination) {
        var request = allocation.getRequestItem().getRequest();
        if (!request.getDestination().getId().equals(destination.getId())
                || !allocation.getInventory().getLocation().getOrganization().getId()
                        .equals(destination.getOrganization().getId())) {
            throw new BusinessRuleViolationException("All allocations must match the shipment destination and organization");
        }
        if (!Set.of(RequestStatus.APPROVED, RequestStatus.PARTIALLY_FULFILLED, RequestStatus.FULFILLED,
                RequestStatus.PARTIALLY_DELIVERED).contains(request.getStatus())) {
            throw new BusinessRuleViolationException("Request is not eligible for shipment operations");
        }
    }

    private void allocationStatus(Allocation allocation, AllocationStatus next) {
        var before = allocation.getStatus();
        allocation.setStatus(next);
        audit.record(AuditAction.STATUS_CHANGED, "ALLOCATION", allocation.getId(),
                allocation.getInventory().getLocation().getOrganization().getId(), "allocation.shipment_status_changed",
                Map.of("before", before, "after", next, "shipmentId", allocation.getShipment().getId()));
    }

    private void shipmentStatus(Shipment shipment, ShipmentStatus next) {
        var before = shipment.getStatus();
        shipment.setStatus(next);
        audit.record(AuditAction.STATUS_CHANGED, "SHIPMENT", shipment.getId(), owner(shipment),
                "shipment.status_changed", Map.of("before", before, "after", next));
    }

    private void stockEvent(Inventory inventory, BigDecimal before, BigDecimal reserved, Shipment shipment, String operation) {
        audit.record(AuditAction.UPDATED, "INVENTORY", inventory.getId(), owner(shipment), operation,
                Map.of("shipmentId", shipment.getId(), "beforeQuantity", before, "afterQuantity", inventory.getQuantity(),
                        "beforeReservedQuantity", reserved, "afterReservedQuantity", inventory.getReservedQuantity()));
    }

    private UUID owner(Shipment shipment) { return shipment.getDestination().getOrganization().getId(); }
    private ResourceNotFoundException missing(String type) { return new ResourceNotFoundException(type + " not found"); }
}
