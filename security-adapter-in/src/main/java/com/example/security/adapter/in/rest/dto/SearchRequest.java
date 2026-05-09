package com.example.security.adapter.in.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SearchRequest(
    @NotBlank String tenantId,
    String query,
    Map<String, String> filters,
    Instant from,
    Instant to,
    List<String> facets,
    @Min(0) @Max(100) Integer facetSize,
    @Min(1) @Max(1000) Integer size,
    String cursor) {}
