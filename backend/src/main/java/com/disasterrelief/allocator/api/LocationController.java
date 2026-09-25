package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.LocationCreateRequest;
import com.disasterrelief.allocator.api.dto.ApiResponses.LocationResponse;
import com.disasterrelief.allocator.service.LocationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'PARTNER_MANAGER')")
    public LocationResponse create(@Valid @RequestBody LocationCreateRequest request) {
        return ApiMapper.location(service.create(request.name(), request.address(), request.organizationId(),
                request.latitude(), request.longitude()));
    }

    @GetMapping("/{id}")
    public LocationResponse find(@PathVariable UUID id) { return ApiMapper.location(service.find(id)); }
}