package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    @EntityGraph(attributePaths = {"organization", "roles"})
    Optional<UserProfile> findByExternalIssuerAndExternalSubject(String issuer, String subject);

    @Override
    @EntityGraph(attributePaths = {"organization", "roles"})
    Optional<UserProfile> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"organization", "roles"})
    Page<UserProfile> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "roles"})
    List<UserProfile> findByOrganizationId(UUID organizationId);

    List<UserProfile> findByRolesContaining(Role role);
    long countByActiveTrueAndRolesContaining(Role role);
}
