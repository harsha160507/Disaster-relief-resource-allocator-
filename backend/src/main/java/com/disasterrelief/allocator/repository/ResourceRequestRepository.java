package com.disasterrelief.allocator.repository;

import com.disasterrelief.allocator.domain.RequestStatus;
import com.disasterrelief.allocator.domain.ResourceRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, UUID> {

    Optional<ResourceRequest> findByReference(String reference);

    List<ResourceRequest> findByDisasterIdAndStatus(UUID disasterId, RequestStatus status);

    List<ResourceRequest> findByRequestedById(UUID userId);

    List<ResourceRequest> findByStatus(RequestStatus status);
}
