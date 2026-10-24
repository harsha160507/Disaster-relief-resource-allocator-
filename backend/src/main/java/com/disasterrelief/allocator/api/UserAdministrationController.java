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
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
@RequiredArgsConstructor
public class UserAdministrationController {
    private final AdministrationService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody UserCreate request) { return service.createUser(request); }
    @GetMapping public PageView<UserView> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.users(page, size); }
    @GetMapping("/{id}") public UserView find(@PathVariable UUID id) { return service.user(id); }
    @PutMapping("/{id}") public UserView update(@PathVariable UUID id,
            @Valid @RequestBody UserUpdate request) { return service.updateUser(id, request); }
}
