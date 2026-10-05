package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.Inventory;
import com.disasterrelief.allocator.domain.Location;
import com.disasterrelief.allocator.domain.ResourceType;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.repository.InventoryRepository;
import com.disasterrelief.allocator.repository.LocationRepository;
import com.disasterrelief.allocator.repository.ResourceTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceTypeRepository resourceTypeRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public ResourceType createResourceType(String name, String unitOfMeasure, boolean perishable) {
        requireText(name, "Resource type name is required");
        requireText(unitOfMeasure, "Unit of measure is required");
        if (resourceTypeRepository.existsByNameIgnoreCase(name.trim())) {
            throw new BusinessRuleViolationException("Resource type already exists: " + name);
        }
        ResourceType resourceType = new ResourceType();
        resourceType.setName(name.trim());
        resourceType.setUnitOfMeasure(unitOfMeasure.trim());
        resourceType.setPerishable(perishable);
        return resourceTypeRepository.save(resourceType);
    }

    @Transactional
    public Inventory receive(UUID locationId, UUID resourceTypeId, BigDecimal quantity) {
        requirePositive(quantity, "Received quantity must be positive");
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
        ResourceType resourceType = findResourceType(resourceTypeId);
        Inventory inventory = inventoryRepository.findByLocationIdAndResourceTypeId(locationId, resourceTypeId)
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setLocation(location);
                    created.setResourceType(resourceType);
                    return created;
                });
        inventory.setQuantity(inventory.getQuantity().add(quantity));
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory adjustQuantity(UUID inventoryId, BigDecimal change) {
        if (change == null || change.signum() == 0) {
            throw new BusinessRuleViolationException("Inventory adjustment cannot be zero");
        }
        Inventory inventory = findInventory(inventoryId);
        BigDecimal updatedQuantity = inventory.getQuantity().add(change);
        if (updatedQuantity.compareTo(inventory.getReservedQuantity()) < 0) {
            throw new BusinessRuleViolationException("Inventory cannot fall below its reserved quantity");
        }
        inventory.setQuantity(updatedQuantity);
        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public List<Inventory> available(UUID resourceTypeId) {
        findResourceType(resourceTypeId);
        return inventoryRepository.findAvailableByResourceTypeId(resourceTypeId);
    }

    @Transactional(readOnly = true)
    public Inventory findInventory(UUID inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found: " + inventoryId));
    }

    @Transactional(readOnly = true)
    public ResourceType findResourceType(UUID resourceTypeId) {
        return resourceTypeRepository.findById(resourceTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource type not found: " + resourceTypeId));
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
    }
}
