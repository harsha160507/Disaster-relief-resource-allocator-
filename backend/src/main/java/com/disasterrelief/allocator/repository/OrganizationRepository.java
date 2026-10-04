package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
