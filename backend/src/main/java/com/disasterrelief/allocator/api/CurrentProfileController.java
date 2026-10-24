package com.disasterrelief.allocator.api;

import com.disasterrelief.allocator.api.dto.AdministrationDtos.*;
import com.disasterrelief.allocator.security.*;
import com.disasterrelief.allocator.service.AdministrationService;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class CurrentProfileController {
    private final CurrentActor actor;
    private final AdministrationService administration;
    @GetMapping public UserView profile() { return administration.user(actor.current().profileId()); }
    @GetMapping("/organization") public OrganizationView organization() {
        var id = actor.current().organizationId();
        if (id == null) throw new ResourceNotFoundException("This profile has no organization");
        return administration.organization(id);
    }
}
