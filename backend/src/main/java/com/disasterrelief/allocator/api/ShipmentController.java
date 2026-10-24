package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.ShipmentDtos;
import com.disasterrelief.allocator.domain.ShipmentStatus;
import com.disasterrelief.allocator.security.OrganizationAccess;
import com.disasterrelief.allocator.service.ShipmentService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR', 'FIELD_AGENT', 'PARTNER_MANAGER', 'VIEWER')")
public class ShipmentController {
    private final ShipmentService service;
    private final OrganizationAccess access;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR')")
    public ShipmentDtos.View create(@Valid @RequestBody ShipmentDtos.Create input) {
        access.location(input.destinationId());
        input.allocationIds().forEach(access::allocation);
        return service.create(input);
    }

    @GetMapping("/{id}")
    public ShipmentDtos.View get(@PathVariable UUID id) {
        access.shipment(id);
        return service.get(id);
    }

    @GetMapping
    public ShipmentDtos.PageView search(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID destinationId,
            @RequestParam(required = false) ShipmentStatus status) {
        UUID scope = organizationId == null ? access.inventoryScope() : access.organizationForCreate(organizationId);
        if (destinationId != null) access.location(destinationId);
        return service.search(page, size, scope, destinationId, status);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR')")
    public ShipmentDtos.View dispatch(@PathVariable UUID id) {
        access.shipment(id);
        return service.dispatch(id);
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR', 'FIELD_AGENT')")
    public ShipmentDtos.View deliver(@PathVariable UUID id) {
        access.shipment(id);
        return service.deliver(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR')")
    public ShipmentDtos.View cancel(@PathVariable UUID id) {
        access.shipment(id);
        return service.cancel(id);
    }
}
