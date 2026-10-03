package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Location;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByOrganizationId(UUID organizationId);
}
