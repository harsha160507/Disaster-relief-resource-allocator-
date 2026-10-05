package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterStatus;
import com.disasterrelief.allocator.domain.Location;
import com.disasterrelief.allocator.domain.RequestPriority;
import com.disasterrelief.allocator.domain.RequestStatus;
import com.disasterrelief.allocator.domain.ResourceRequest;
import com.disasterrelief.allocator.domain.ResourceRequestItem;
import com.disasterrelief.allocator.domain.ResourceType;
import com.disasterrelief.allocator.domain.UserProfile;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.repository.ResourceRequestRepository;
import com.disasterrelief.allocator.repository.UserProfileRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final ResourceRequestRepository requestRepository;
    private final UserProfileRepository userProfileRepository;
    private final DisasterService disasterService;
    private final LocationService locationService;
    private final ResourceService resourceService;

    @Transactional
    public ResourceRequest create(String reference, UUID disasterId, UUID requesterId,
            UUID destinationId, RequestPriority priority, String notes) {
        requireText(reference, "Request reference is required");
        if (priority == null) {
            throw new BusinessRuleViolationException("Request priority is required");
        }
        if (requestRepository.findByReference(reference.trim()).isPresent()) {
            throw new BusinessRuleViolationException("Request reference already exists: " + reference);
        }
        Disaster disaster = disasterService.find(disasterId);
        if (disaster.getStatus() != DisasterStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Requests require an active disaster");
        }
        UserProfile requester = userProfileRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found: " + requesterId));
        Location destination = locationService.find(destinationId);
        if (!disaster.getAffectedLocations().contains(destination)) {
            throw new BusinessRuleViolationException("Request destination is not affected by the disaster");
        }
        ResourceRequest request = new ResourceRequest();
        request.setReference(reference.trim());
        request.setDisaster(disaster);
        request.setRequestedBy(requester);
        request.setDestination(destination);
        request.setPriority(priority);
        request.setNotes(notes);
        return requestRepository.save(request);
    }

    @Transactional
    public ResourceRequest addItem(UUID requestId, UUID resourceTypeId, BigDecimal quantity) {
        requirePositive(quantity);
        ResourceRequest request = find(requestId);
        requireStatus(request, RequestStatus.DRAFT, "Items can only be added to draft requests");
        ResourceType resourceType = resourceService.findResourceType(resourceTypeId);
        boolean alreadyRequested = request.getItems().stream()
                .anyMatch(item -> item.getResourceType().getId().equals(resourceTypeId));
        if (alreadyRequested) {
            throw new BusinessRuleViolationException("Resource type is already on this request");
        }
        ResourceRequestItem item = new ResourceRequestItem();
        item.setRequest(request);
        item.setResourceType(resourceType);
        item.setRequestedQuantity(quantity);
        request.getItems().add(item);
        return request;
    }

    @Transactional
    public ResourceRequest submit(UUID requestId) {
        ResourceRequest request = find(requestId);
        requireStatus(request, RequestStatus.DRAFT, "Only draft requests can be submitted");
        if (request.getItems().isEmpty()) {
            throw new BusinessRuleViolationException("A request must contain at least one resource item");
        }
        request.setStatus(RequestStatus.SUBMITTED);
        return request;
    }

    @Transactional
    public ResourceRequest approve(UUID requestId) {
        ResourceRequest request = find(requestId);
        requireStatus(request, RequestStatus.SUBMITTED, "Only submitted requests can be approved");
        request.setStatus(RequestStatus.APPROVED);
        return request;
    }

    @Transactional
    public ResourceRequest reject(UUID requestId) {
        ResourceRequest request = find(requestId);
        if (request.getStatus() != RequestStatus.SUBMITTED) {
            throw new BusinessRuleViolationException("Only submitted requests can be rejected");
        }
        request.setStatus(RequestStatus.REJECTED);
        return request;
    }

    @Transactional(readOnly = true)
    public ResourceRequest find(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource request not found: " + requestId));
    }

    private void requireStatus(ResourceRequest request, RequestStatus expected, String message) {
        if (request.getStatus() != expected) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleViolationException("Requested quantity must be positive");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
    }
}
