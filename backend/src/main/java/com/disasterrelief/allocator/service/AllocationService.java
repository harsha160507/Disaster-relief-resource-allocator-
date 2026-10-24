package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.Allocation;
import com.disasterrelief.allocator.domain.AllocationStatus;
import com.disasterrelief.allocator.domain.Inventory;
import com.disasterrelief.allocator.domain.RequestStatus;
import com.disasterrelief.allocator.domain.ResourceRequest;
import com.disasterrelief.allocator.domain.ResourceRequestItem;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.repository.AllocationRepository;
import com.disasterrelief.allocator.repository.InventoryRepository;
import com.disasterrelief.allocator.repository.ResourceRequestItemRepository;
import com.disasterrelief.allocator.repository.ResourceRequestRepository;
import java.math.BigDecimal;
import java.util.Map;
import com.disasterrelief.allocator.domain.AuditAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final ResourceRequestRepository requestRepository;
    private final ResourceRequestItemRepository requestItemRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditWriter audit;

    @Transactional
    public Allocation reserve(UUID requestItemId, UUID inventoryId, BigDecimal quantity) {
        requirePositive(quantity);
        UUID requestId = requestItemRepository.requestIdForItem(requestItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Request item not found: " + requestItemId));
        ResourceRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource request not found: " + requestId));
        ResourceRequestItem item = requestItemRepository.findById(requestItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Request item not found: " + requestItemId));
        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + inventoryId));
        if (!inventory.getLocation().getOrganization().getId().equals(request.getDestination().getOrganization().getId())) {
            throw new BusinessRuleViolationException("Inventory and request must belong to the same organization");
        }
        if (request.getStatus() != RequestStatus.APPROVED
                && request.getStatus() != RequestStatus.PARTIALLY_FULFILLED
                && request.getStatus() != RequestStatus.PARTIALLY_DELIVERED) {
            throw new BusinessRuleViolationException("Only approved requests can receive allocations");
        }
        if (!inventory.getResourceType().getId().equals(item.getResourceType().getId())) {
            throw new BusinessRuleViolationException("Inventory resource does not match the request item");
        }
        BigDecimal remainingNeed = item.getRequestedQuantity().subtract(item.getAllocatedQuantity());
        if (quantity.compareTo(remainingNeed) > 0) {
            throw new BusinessRuleViolationException("Allocation exceeds the remaining request quantity");
        }
        BigDecimal available = inventory.getQuantity().subtract(inventory.getReservedQuantity());
        if (quantity.compareTo(available) > 0) {
            throw new BusinessRuleViolationException("Insufficient available inventory");
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity().add(quantity));
        item.setAllocatedQuantity(item.getAllocatedQuantity().add(quantity));
        Allocation allocation = new Allocation();
        allocation.setRequestItem(item);
        allocation.setInventory(inventory);
        allocation.setQuantity(quantity);
        allocation.setStatus(AllocationStatus.RESERVED);
        inventoryRepository.save(inventory);
        requestItemRepository.save(item);
        Allocation saved = allocationRepository.save(allocation);
        reservation(saved);
        updateRequestStatus(request);
        return saved;
    }

    @Transactional
    public List<Allocation> allocate(UUID requestId) {
        ResourceRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource request not found: " + requestId));
        if (request.getStatus() != RequestStatus.APPROVED
                && request.getStatus() != RequestStatus.PARTIALLY_FULFILLED
                && request.getStatus() != RequestStatus.PARTIALLY_DELIVERED) {
            throw new BusinessRuleViolationException("Only approved requests can be allocated");
        }

        List<Allocation> allocations = new ArrayList<>();
        var resourceIds = request.getItems().stream().map(i -> i.getResourceType().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<Inventory> candidates = resourceIds.isEmpty() ? List.of()
                : inventoryRepository.findAvailableForTypesForUpdate(resourceIds,
                        request.getDestination().getOrganization().getId());
        for (ResourceRequestItem item : request.getItems()) {
            BigDecimal remainingNeed = item.getRequestedQuantity().subtract(item.getAllocatedQuantity());
            if (remainingNeed.signum() <= 0) {
                continue;
            }
            List<Inventory> inventories = candidates.stream()
                    .filter(i -> i.getResourceType().getId().equals(item.getResourceType().getId())).toList();
            for (Inventory inventory : inventories) {
                if (!inventory.getLocation().getOrganization().getId()
                        .equals(request.getDestination().getOrganization().getId())) continue;
                BigDecimal available = inventory.getQuantity().subtract(inventory.getReservedQuantity());
                BigDecimal quantity = remainingNeed.min(available);
                if (quantity.signum() <= 0) {
                    continue;
                }
                inventory.setReservedQuantity(inventory.getReservedQuantity().add(quantity));
                item.setAllocatedQuantity(item.getAllocatedQuantity().add(quantity));
                remainingNeed = remainingNeed.subtract(quantity);

                Allocation allocation = new Allocation();
                allocation.setRequestItem(item);
                allocation.setInventory(inventory);
                allocation.setQuantity(quantity);
                allocation.setStatus(AllocationStatus.RESERVED);
                inventoryRepository.save(inventory);
                requestItemRepository.save(item);
                Allocation saved = allocationRepository.save(allocation);
                reservation(saved);
                allocations.add(saved);
                if (remainingNeed.signum() == 0) {
                    break;
                }
            }
        }
        updateRequestStatus(request);
        return allocations;
    }

    @Transactional(readOnly = true)
    public List<Allocation> history(UUID requestItemId) {
        requestItemRepository.findById(requestItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Request item not found: " + requestItemId));
        return allocationRepository.findByRequestItemId(requestItemId);
    }

    private void updateRequestStatus(ResourceRequest request) {
        RequestProgress.update(request, audit);
    }

    private void reservation(Allocation allocation) {
        var item = allocation.getRequestItem();
        var inventory = allocation.getInventory();
        audit.record(AuditAction.CREATED, "ALLOCATION", allocation.getId(),
                item.getRequest().getDestination().getOrganization().getId(),
                "allocation.reserved", Map.of("requestId", item.getRequest().getId(), "requestItemId", item.getId(),
                        "inventoryId", inventory.getId(), "quantity", allocation.getQuantity(),
                        "status", allocation.getStatus(),
                        "beforeReservedQuantity", inventory.getReservedQuantity().subtract(allocation.getQuantity()),
                        "afterReservedQuantity", inventory.getReservedQuantity(),
                        "beforeAllocatedQuantity", item.getAllocatedQuantity().subtract(allocation.getQuantity()),
                        "afterAllocatedQuantity", item.getAllocatedQuantity()));
    }

    private void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleViolationException("Allocation quantity must be positive");
        }
    }
}
