package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface DisasterRepository extends JpaRepository<Disaster, UUID> {

    @Override
    @EntityGraph(attributePaths = "affectedLocations")
    Optional<Disaster> findById(UUID disasterId);

    List<Disaster> findByStatus(DisasterStatus status);

    List<Disaster> findByAffectedLocationsId(UUID locationId);
}
