package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.ResourceRequestItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRequestItemRepository extends JpaRepository<ResourceRequestItem, UUID> {

    @org.springframework.data.jpa.repository.Query("select i.request.id from ResourceRequestItem i where i.id = :id")
    java.util.Optional<UUID> requestIdForItem(@org.springframework.data.repository.query.Param("id") UUID id);

    List<ResourceRequestItem> findByRequestId(UUID requestId);
}
