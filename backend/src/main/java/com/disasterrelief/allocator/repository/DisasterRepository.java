package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisasterRepository extends JpaRepository<Disaster, UUID> {

    List<Disaster> findByStatus(DisasterStatus status);

    List<Disaster> findByAffectedLocationsId(UUID locationId);
}
