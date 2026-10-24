package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.DisasterCloseRequest;
import com.disasterrelief.allocator.api.dto.DisasterCreateRequest;
import com.disasterrelief.allocator.api.dto.ApiResponses.DisasterResponse;
import com.disasterrelief.allocator.service.DisasterService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/disasters")
@RequiredArgsConstructor
public class DisasterController {
    private final DisasterService service;
    private final com.disasterrelief.allocator.security.OrganizationAccess access;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISASTER_COORDINATOR')")
    public DisasterResponse create(@Valid @RequestBody DisasterCreateRequest request) {
        return ApiMapper.disaster(service.createForOrganization(request.name(), request.type(), request.description(), request.startedAt(),
                access.organizationForCreate(request.organizationId())));
    }

    @GetMapping("/{id}")
    public DisasterResponse find(@PathVariable UUID id) { access.disaster(id); return ApiMapper.disaster(service.find(id)); }

    @PostMapping("/{id}/activation")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISASTER_COORDINATOR')")
    public DisasterResponse activate(@PathVariable UUID id) { access.disaster(id); return ApiMapper.disaster(service.activate(id)); }

    @PostMapping("/{id}/closure")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISASTER_COORDINATOR')")
    public DisasterResponse close(@PathVariable UUID id, @Valid @RequestBody DisasterCloseRequest request) {
        access.disaster(id);
        return ApiMapper.disaster(service.close(id, request.endedAt()));
    }

    @PutMapping("/{id}/affected-locations/{locationId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISASTER_COORDINATOR')")
    public DisasterResponse addLocation(@PathVariable UUID id, @PathVariable UUID locationId) {
        access.affectedLocation(id, locationId);
        return ApiMapper.disaster(service.addAffectedLocation(id, locationId));
    }
}