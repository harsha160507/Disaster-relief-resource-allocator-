package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Inventory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Override
    @EntityGraph(attributePaths = {"location", "resourceType"})
    Optional<Inventory> findById(UUID inventoryId);

    @EntityGraph(attributePaths = {"location", "resourceType"})
    List<Inventory> findByLocationId(UUID locationId);

    @EntityGraph(attributePaths = {"location", "resourceType"})
    List<Inventory> findByResourceTypeId(UUID resourceTypeId);

    @EntityGraph(attributePaths = {"location", "resourceType"})
    Optional<Inventory> findByLocationIdAndResourceTypeId(UUID locationId, UUID resourceTypeId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = {"location", "resourceType"})
        @Query("select inventory from Inventory inventory where inventory.id = :inventoryId")
        Optional<Inventory> findByIdForUpdate(@Param("inventoryId") UUID inventoryId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @EntityGraph(attributePaths = {"location", "resourceType"})
        @Query("select inventory from Inventory inventory "
            + "where inventory.resourceType.id = :resourceTypeId "
            + "and inventory.quantity > inventory.reservedQuantity order by inventory.id")
        List<Inventory> findAvailableByResourceTypeIdForUpdate(@Param("resourceTypeId") UUID resourceTypeId);

    @EntityGraph(attributePaths = {"location", "resourceType"})
    @Query("select inventory from Inventory inventory "
            + "where inventory.resourceType.id = :resourceTypeId "
            + "and inventory.quantity > inventory.reservedQuantity")
    List<Inventory> findAvailableByResourceTypeId(@Param("resourceTypeId") UUID resourceTypeId);
}
