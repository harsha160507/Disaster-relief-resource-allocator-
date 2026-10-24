package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterStatus;
import com.disasterrelief.allocator.domain.DisasterType;
import com.disasterrelief.allocator.domain.Location;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.repository.DisasterRepository;
import com.disasterrelief.allocator.repository.LocationRepository;
import com.disasterrelief.allocator.repository.OrganizationRepository;
import java.time.Instant;
import java.util.Map;
import com.disasterrelief.allocator.domain.AuditAction;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisasterService {

    private final DisasterRepository disasterRepository;
    private final LocationRepository locationRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditWriter audit;

    @Transactional
    public Disaster createForOrganization(String name, DisasterType type, String description, Instant startedAt, UUID organizationId) {
        var organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (!organization.isActive()) throw new BusinessRuleViolationException("Organization is inactive");
        var disaster = newDisaster(name, type, description, startedAt);
        disaster.setOrganization(organization);
        disasterRepository.save(disaster);
        created(disaster);
        return disaster;
    }

    @Transactional
    public Disaster create(String name, DisasterType type, String description, Instant startedAt) {
        var disaster = disasterRepository.save(newDisaster(name, type, description, startedAt));
        created(disaster);
        return disaster;
    }

    private Disaster newDisaster(String name, DisasterType type, String description, Instant startedAt) {
        requireText(name, "Disaster name is required");
        if (type == null || startedAt == null) {
            throw new BusinessRuleViolationException("Disaster type and start time are required");
        }
        Disaster disaster = new Disaster();
        disaster.setName(name.trim());
        disaster.setType(type);
        disaster.setDescription(description);
        disaster.setStartedAt(startedAt);
        return disaster;
    }

    @Transactional
    public Disaster activate(UUID disasterId) {
        Disaster disaster = find(disasterId);
        requireStatus(disaster, DisasterStatus.DRAFT, "Only draft disasters can be activated");
        disaster.setStatus(DisasterStatus.ACTIVE);
        status(disaster, DisasterStatus.DRAFT);
        return disaster;
    }

    @Transactional
    public Disaster close(UUID disasterId, Instant endedAt) {
        Disaster disaster = find(disasterId);
        requireStatus(disaster, DisasterStatus.ACTIVE, "Only active disasters can be closed");
        if (endedAt == null || endedAt.isBefore(disaster.getStartedAt())) {
            throw new BusinessRuleViolationException("End time must be on or after the start time");
        }
        disaster.setEndedAt(endedAt);
        disaster.setStatus(DisasterStatus.CLOSED);
        status(disaster, DisasterStatus.ACTIVE);
        return disaster;
    }

    @Transactional
    public Disaster addAffectedLocation(UUID disasterId, UUID locationId) {
        Disaster disaster = find(disasterId);
        requireStatus(disaster, DisasterStatus.ACTIVE, "Locations can only be added to active disasters");
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
        if (disaster.getAffectedLocations().add(location)) {
            audit.record(AuditAction.UPDATED, "DISASTER", disaster.getId(), owner(disaster),
                    "disaster.location_added", Map.of("locationId", locationId));
        }
        return disaster;
    }

    @Transactional(readOnly = true)
    public Disaster find(UUID disasterId) {
        return disasterRepository.findById(disasterId)
                .orElseThrow(() -> new ResourceNotFoundException("Disaster not found: " + disasterId));
    }

    private UUID owner(Disaster disaster) {
        return disaster.getOrganization() == null ? null : disaster.getOrganization().getId();
    }

    private void created(Disaster disaster) {
        audit.record(AuditAction.CREATED, "DISASTER", disaster.getId(), owner(disaster),
                "disaster.created", Map.of("status", disaster.getStatus(), "type", disaster.getType()));
    }

    private void status(Disaster disaster, DisasterStatus before) {
        audit.record(AuditAction.STATUS_CHANGED, "DISASTER", disaster.getId(), owner(disaster),
                "disaster.status_changed", Map.of("before", before, "after", disaster.getStatus()));
    }

    private void requireStatus(Disaster disaster, DisasterStatus expected, String message) {
        if (disaster.getStatus() != expected) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
    }
}
