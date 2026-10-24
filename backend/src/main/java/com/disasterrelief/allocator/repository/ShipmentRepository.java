package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Shipment;
import com.disasterrelief.allocator.domain.ShipmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Shipment> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Shipment> findByTrackingReference(String trackingReference);

    List<Shipment> findByStatus(ShipmentStatus status);

    List<Shipment> findByDestinationId(UUID destinationId);
}
