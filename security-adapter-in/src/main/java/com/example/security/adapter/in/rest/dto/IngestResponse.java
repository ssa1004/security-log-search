package com.example.security.adapter.in.rest.dto;

import java.util.UUID;

public record IngestResponse(UUID eventId, boolean duplicate) {}
