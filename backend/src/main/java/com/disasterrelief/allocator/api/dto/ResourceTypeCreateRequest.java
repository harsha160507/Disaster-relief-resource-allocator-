package com.disasterrelief.allocator.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceTypeCreateRequest(@NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 30) String unitOfMeasure, boolean perishable) { }