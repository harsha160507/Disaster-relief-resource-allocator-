package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Allocation;
import com.disasterrelief.allocator.domain.AllocationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    @EntityGraph(attributePaths = {"requestItem", "requestItem.resourceType", "inventory", "shipment"})
    List<Allocation> findByRequestItemId(UUID requestItemId);

    List<Allocation> findByInventoryId(UUID inventoryId);

    List<Allocation> findByStatus(AllocationStatus status);

    List<Allocation> findByShipmentId(UUID shipmentId);
}
