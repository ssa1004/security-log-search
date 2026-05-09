package com.example.security.adapter.in.rest.dto;

import com.example.security.domain.sigma.SigmaRule;
import java.time.Instant;
import java.util.List;

/** 조회 응답 — Sigma 룰 메타데이터 (원본 YAML 은 별도 endpoint 로 노출). */
public record SigmaRuleResponse(
    String id,
    String title,
    String level,
    String status,
    String author,
    String description,
    List<String> tags,
    List<String> references,
    String logsourceCategory,
    String logsourceProduct,
    Instant importedAt) {

  public static SigmaRuleResponse from(SigmaRule s) {
    return new SigmaRuleResponse(
        s.id(),
        s.title(),
        s.level(),
        s.status(),
        s.author(),
        s.description(),
        s.tags(),
        s.references(),
        s.logsource().get("category"),
        s.logsource().get("product"),
        s.importedAt());
  }
}
