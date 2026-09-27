package com.disasterrelief.allocator.api.dto;

import com.disasterrelief.allocator.domain.DisasterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record DisasterCreateRequest(@NotBlank @Size(max = 160) String name,
        @NotNull DisasterType type, @Size(max = 2000) String description,
        @NotNull Instant startedAt) { }