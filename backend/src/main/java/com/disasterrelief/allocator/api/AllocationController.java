package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.AllocationCreateRequest;
import com.disasterrelief.allocator.api.dto.ApiResponses.AllocationResponse;
import com.disasterrelief.allocator.service.AllocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/allocations")
@RequiredArgsConstructor
public class AllocationController {
    private final AllocationService service;

    @GetMapping("/request-items/{requestItemId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR', 'FIELD_AGENT', 'PARTNER_MANAGER', 'VIEWER')")
    public List<AllocationResponse> history(@PathVariable UUID requestItemId) {
        return service.history(requestItemId).stream().map(ApiMapper::allocation).toList();
    }

    @PostMapping("/requests/{requestId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR')")
    public List<AllocationResponse> allocate(@PathVariable UUID requestId) {
        return service.allocate(requestId).stream().map(ApiMapper::allocation).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR')")
    public AllocationResponse reserve(@Valid @RequestBody AllocationCreateRequest request) {
        return ApiMapper.allocation(service.reserve(request.requestItemId(), request.inventoryId(), request.quantity()));
    }
}