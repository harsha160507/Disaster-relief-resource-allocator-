package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Shipment;
import com.disasterrelief.allocator.domain.ShipmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByTrackingReference(String trackingReference);

    List<Shipment> findByStatus(ShipmentStatus status);

    List<Shipment> findByDestinationId(UUID destinationId);
}
