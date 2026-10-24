package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.AdministrationDtos.*;
import com.disasterrelief.allocator.service.AdministrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/organizations")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class OrganizationAdministrationController {
    private final AdministrationService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrganizationView create(@Valid @RequestBody OrganizationCreate request) {
        return service.createOrganization(request);
    }
    @GetMapping public PageView<OrganizationView> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.organizations(page, size); }
    @GetMapping("/{id}") public OrganizationView find(@PathVariable UUID id) { return service.organization(id); }
    @PutMapping("/{id}") public OrganizationView update(@PathVariable UUID id,
            @Valid @RequestBody OrganizationUpdate request) { return service.updateOrganization(id, request); }
}
