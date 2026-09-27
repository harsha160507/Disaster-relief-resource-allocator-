package com.disasterrelief.allocator.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record DisasterCloseRequest(@NotNull Instant endedAt) { }