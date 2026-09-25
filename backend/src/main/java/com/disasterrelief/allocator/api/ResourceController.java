package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.*;
import com.disasterrelief.allocator.api.dto.ApiResponses.*;
import com.disasterrelief.allocator.service.ResourceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService service;

    @PostMapping("/resource-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER')")
    public ResourceTypeResponse createType(@Valid @RequestBody ResourceTypeCreateRequest request) {
        return ApiMapper.resourceType(service.createResourceType(request.name(), request.unitOfMeasure(), request.perishable()));
    }

    @GetMapping("/resource-types/{id}")
    public ResourceTypeResponse findType(@PathVariable UUID id) { return ApiMapper.resourceType(service.findResourceType(id)); }

    @PostMapping("/inventory/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER')")
    public InventoryResponse receive(@Valid @RequestBody InventoryReceiveRequest request) {
        return ApiMapper.inventory(service.receive(request.locationId(), request.resourceTypeId(), request.quantity()));
    }

    @PatchMapping("/inventory/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'INVENTORY_MANAGER')")
    public InventoryResponse adjust(@PathVariable UUID id, @Valid @RequestBody InventoryAdjustRequest request) {
        return ApiMapper.inventory(service.adjustQuantity(id, request.change()));
    }

    @GetMapping("/inventory/resource-types/{resourceTypeId}/available")
    public List<InventoryResponse> available(@PathVariable UUID resourceTypeId) {
        return service.available(resourceTypeId).stream().map(ApiMapper::inventory).toList();
    }

    @GetMapping("/inventory/{id}")
    public InventoryResponse findInventory(@PathVariable UUID id) { return ApiMapper.inventory(service.findInventory(id)); }
}