package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Allocation;
import com.disasterrelief.allocator.domain.AllocationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Allocation a where a.id = :id")
    java.util.Optional<Allocation> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("select a.requestItem.request.id from Allocation a where a.id = :id")
    java.util.Optional<UUID> requestIdForAllocation(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("select a.id from Allocation a where a.shipment.id = :id order by a.id")
    List<UUID> idsForShipment(@org.springframework.data.repository.query.Param("id") UUID id);

    @EntityGraph(attributePaths = {"requestItem", "requestItem.resourceType", "inventory", "shipment"})
    List<Allocation> findByRequestItemId(UUID requestItemId);

    List<Allocation> findByInventoryId(UUID inventoryId);

    List<Allocation> findByStatus(AllocationStatus status);

    List<Allocation> findByShipmentId(UUID shipmentId);
}
