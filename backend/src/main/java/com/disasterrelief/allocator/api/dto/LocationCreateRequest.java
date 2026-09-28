package com.disasterrelief.allocator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record LocationCreateRequest(@NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 255) String address, @NotNull UUID organizationId,
        BigDecimal latitude, BigDecimal longitude) { }