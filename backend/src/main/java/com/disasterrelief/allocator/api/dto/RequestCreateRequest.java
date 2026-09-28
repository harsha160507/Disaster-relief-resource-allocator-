package com.disasterrelief.allocator.api.dto;

import com.disasterrelief.allocator.domain.RequestPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequestCreateRequest(@NotBlank @Size(max = 40) String reference,
        @NotNull UUID disasterId, @NotNull UUID requesterId, @NotNull UUID destinationId,
        @NotNull RequestPriority priority, @Size(max = 2000) String notes) { }