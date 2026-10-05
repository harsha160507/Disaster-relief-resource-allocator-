package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.Role;
import com.disasterrelief.allocator.domain.UserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByExternalSubject(String externalSubject);

    List<UserProfile> findByOrganizationId(UUID organizationId);

    List<UserProfile> findByRolesContaining(Role role);
}
