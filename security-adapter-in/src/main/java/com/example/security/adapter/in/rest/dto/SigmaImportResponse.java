package com.example.security.adapter.in.rest.dto;

import java.util.List;
import java.util.UUID;

/** Sigma import 결과 — 변환된 alert_rule_id 목록 + 변환 한계 (운영자 후속 검토 항목). */
public record SigmaImportResponse(
    int importedCount, List<RuleSummary> rules, List<MappingNote> mappingNotes) {

  public record RuleSummary(UUID alertRuleId, String sigmaId, String title, String level) {}

  public record MappingNote(UUID alertRuleId, String sigmaId, List<String> unsupported) {}
}
