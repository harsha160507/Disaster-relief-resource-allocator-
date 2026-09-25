package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.*;
import com.disasterrelief.allocator.api.dto.ApiResponses.RequestResponse;
import com.disasterrelief.allocator.service.RequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {
    private final RequestService service;

    @GetMapping
    public ResponseEntity<Void> collectionNotAvailable() {
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'FIELD_AGENT', 'PARTNER_MANAGER')")
    public RequestResponse create(@Valid @RequestBody RequestCreateRequest request) {
        return ApiMapper.request(service.create(request.reference(), request.disasterId(), request.requesterId(),
                request.destinationId(), request.priority(), request.notes()));
    }

    @GetMapping("/{id}")
    public RequestResponse find(@PathVariable UUID id) { return ApiMapper.request(service.find(id)); }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'FIELD_AGENT', 'PARTNER_MANAGER')")
    public RequestResponse addItem(@PathVariable UUID id, @Valid @RequestBody RequestItemCreateRequest request) {
        return ApiMapper.request(service.addItem(id, request.resourceTypeId(), request.quantity()));
    }

    @PostMapping("/{id}/submission")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'FIELD_AGENT', 'PARTNER_MANAGER')")
    public RequestResponse submit(@PathVariable UUID id) { return ApiMapper.request(service.submit(id)); }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISASTER_COORDINATOR')")
    public RequestResponse approve(@PathVariable UUID id) { return ApiMapper.request(service.approve(id)); }

    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'DISASTER_COORDINATOR')")
    public RequestResponse reject(@PathVariable UUID id) { return ApiMapper.request(service.reject(id)); }
}