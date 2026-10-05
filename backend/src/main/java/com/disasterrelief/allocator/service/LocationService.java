package com.disasterrelief.allocator.service;

import com.disasterrelief.allocator.domain.Location;
import com.disasterrelief.allocator.domain.Organization;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.repository.LocationRepository;
import com.disasterrelief.allocator.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public Location create(String name, String address, UUID organizationId,
            BigDecimal latitude, BigDecimal longitude) {
        if (name == null || name.isBlank() || address == null || address.isBlank()) {
            throw new BusinessRuleViolationException("Location name and address are required");
        }
        validateCoordinate(latitude, -90, 90, "latitude");
        validateCoordinate(longitude, -180, 180, "longitude");
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + organizationId));
        Location location = new Location();
        location.setName(name.trim());
        location.setAddress(address.trim());
        location.setOrganization(organization);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return locationRepository.save(location);
    }

    @Transactional(readOnly = true)
    public Location find(UUID locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
    }

    private void validateCoordinate(BigDecimal value, int minimum, int maximum, String name) {
        if (value != null && (value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0)) {
            throw new BusinessRuleViolationException("Invalid " + name);
        }
    }
}
