package com.example.security.adapter.`in`.rest.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * POST /api/v1/search — 검색 요청.
 *
 * 검증 어노테이션은 controller layer 에서 `@Valid` 와 함께 동작한다. 본 DTO 는 application
 * layer 의 SearchQuery 와 별도로 둔다 — wire format 변경이 도메인을 흔들지 않게 격리.
 */
@JvmRecord
data class SearchRequest(
    @field:NotBlank val tenantId: String,
    val query: String?,
    val filters: Map<String, String>?,
    val from: Instant?,
    val to: Instant?,
    val facets: List<String>?,
    @field:Min(0) @field:Max(100) val facetSize: Int?,
    @field:Min(1) @field:Max(1000) val size: Int?,
    val cursor: String?,
)
