package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.RequestStatus;
import com.disasterrelief.allocator.domain.ResourceRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from ResourceRequest r where r.id = :id")
    Optional<ResourceRequest> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);


    @Override
    @EntityGraph(attributePaths = {"disaster", "requestedBy", "destination", "items", "items.resourceType"})
    Optional<ResourceRequest> findById(UUID requestId);

    Optional<ResourceRequest> findByReference(String reference);

    List<ResourceRequest> findByDisasterIdAndStatus(UUID disasterId, RequestStatus status);

    List<ResourceRequest> findByRequestedById(UUID userId);

    List<ResourceRequest> findByStatus(RequestStatus status);
}
