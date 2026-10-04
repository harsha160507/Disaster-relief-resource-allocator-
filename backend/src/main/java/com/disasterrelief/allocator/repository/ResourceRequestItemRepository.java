package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.ResourceRequestItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRequestItemRepository extends JpaRepository<ResourceRequestItem, UUID> {

    List<ResourceRequestItem> findByRequestId(UUID requestId);
}
