package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.repository.AuditEventRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AuditHistoryService {
    private final AuditEventRepository events;

    public record EventView(UUID id, Instant createdAt, AuditAction action, String entityType, UUID entityId,
            UUID organizationId, String operation, UUID actorId, UUID actorOrganizationId, String actorRoles,
            String source, UUID correlationId, String details) {}
    public record HistoryPage(List<EventView> content, int page, int size, long totalElements, int totalPages) {}

    public HistoryPage search(int page, int size, String entityType, UUID entityId, UUID actorId,
            UUID organizationId, AuditAction action, Instant from, Instant to, UUID correlationId) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be 0 to 100000 and size 1 to 100");
        }
        if (entityType != null && !Set.of("ORGANIZATION", "USER_PROFILE", "DISASTER", "LOCATION",
                "RESOURCE_TYPE", "INVENTORY", "RESOURCE_REQUEST", "RESOURCE_REQUEST_ITEM", "ALLOCATION", "SHIPMENT").contains(entityType)) {
            throw new IllegalArgumentException("Unknown entity type");
        }
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Invalid time range");
        Specification<AuditEvent> filter = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (entityType != null) predicates.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) predicates.add(cb.equal(root.get("entityId"), entityId));
            if (actorId != null) predicates.add(cb.equal(root.get("actor").get("id"), actorId));
            if (organizationId != null) predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (action != null) predicates.add(cb.equal(root.get("action"), action));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            if (correlationId != null) predicates.add(cb.equal(root.get("correlationId"), correlationId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = events.findAll(filter, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new HistoryPage(result.getContent().stream().map(this::view).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    public EventView get(UUID id) {
        return view(events.findById(id).orElseThrow(() -> new ResourceNotFoundException("Audit event not found")));
    }

    private EventView view(AuditEvent event) {
        return new EventView(event.getId(), event.getCreatedAt(), event.getAction(), event.getEntityType(),
                event.getEntityId(), event.getOrganizationId(), event.getOperation(),
                event.getActor() == null ? null : event.getActor().getId(), event.getActorOrganizationId(),
                event.getActorRoles(), event.getSource(), event.getCorrelationId(), event.getDetails());
    }
}
