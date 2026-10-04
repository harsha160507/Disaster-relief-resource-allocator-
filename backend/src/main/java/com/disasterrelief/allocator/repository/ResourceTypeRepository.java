package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.ResourceType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTypeRepository extends JpaRepository<ResourceType, UUID> {

    List<ResourceType> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
