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

    @Transactional
    public Allocation reserve(UUID requestItemId, UUID inventoryId, BigDecimal quantity) {
        requirePositive(quantity);
        ResourceRequestItem item = requestItemRepository.findById(requestItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Request item not found: " + requestItemId));
        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + inventoryId));
        ResourceRequest request = item.getRequest();
        if (request.getStatus() != RequestStatus.APPROVED
                && request.getStatus() != RequestStatus.PARTIALLY_FULFILLED) {
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
        updateRequestStatus(request);
        return saved;
    }

    @Transactional
    public List<Allocation> allocate(UUID requestId) {
        ResourceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource request not found: " + requestId));
        if (request.getStatus() != RequestStatus.APPROVED
                && request.getStatus() != RequestStatus.PARTIALLY_FULFILLED) {
            throw new BusinessRuleViolationException("Only approved requests can be allocated");
        }

        List<Allocation> allocations = new ArrayList<>();
        for (ResourceRequestItem item : request.getItems()) {
            BigDecimal remainingNeed = item.getRequestedQuantity().subtract(item.getAllocatedQuantity());
            if (remainingNeed.signum() <= 0) {
                continue;
            }
            List<Inventory> inventories = inventoryRepository
                    .findAvailableByResourceTypeIdForUpdate(item.getResourceType().getId());
            for (Inventory inventory : inventories) {
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
                allocations.add(allocationRepository.save(allocation));
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
        boolean hasUnfulfilledItem = request.getItems().stream()
                .anyMatch(item -> item.getAllocatedQuantity().compareTo(item.getRequestedQuantity()) < 0);
        boolean hasAllocation = request.getItems().stream()
                .anyMatch(item -> item.getAllocatedQuantity().signum() > 0);
        if (!hasUnfulfilledItem && !request.getItems().isEmpty()) {
            request.setStatus(RequestStatus.FULFILLED);
        } else if (hasAllocation) {
            request.setStatus(RequestStatus.PARTIALLY_FULFILLED);
        }
    }

    private void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleViolationException("Allocation quantity must be positive");
        }
    }
}
