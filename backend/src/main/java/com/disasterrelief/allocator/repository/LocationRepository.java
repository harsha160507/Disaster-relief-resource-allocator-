package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    @Override
    @EntityGraph(attributePaths = "organization")
    Optional<Location> findById(UUID locationId);

    List<Location> findByOrganizationId(UUID organizationId);
}
