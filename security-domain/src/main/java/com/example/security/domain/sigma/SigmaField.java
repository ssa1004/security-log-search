package com.example.security.domain.sigma;

import java.util.Locale;
import java.util.Set;

/**
 * Sigma detection field 표기 — {@code FieldName|modifier1|modifier2}.
 *
 * <p>Sigma spec 의 modifier 일부:
 *
 * <ul>
 *   <li>{@code contains}, {@code startswith}, {@code endswith} — 부분 매칭
 *   <li>{@code equals} — 정확 매칭 (modifier 생략 시 default 도 equals)
 *   <li>{@code re} — 정규표현식 (본 매퍼는 미지원)
 *   <li>{@code base64} / {@code base64offset} / {@code utf16} 등 — 인코딩 (미지원)
 *   <li>{@code all} — 모든 값이 매칭되어야 함 (미지원)
 * </ul>
 */
public record SigmaField(String name, java.util.List<String> modifiers) {

  /** equals-like 인 modifier set — 본 매퍼가 supported 로 간주. */
  private static final Set<String> EQUALS_LIKE =
      Set.of("equals", "contains", "startswith", "endswith");

  public static SigmaField parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("field 이름이 비어 있음");
    }
    var parts = raw.split("\\|");
    var name = parts[0].trim();
    if (parts.length == 1) {
      return new SigmaField(name, java.util.List.of());
    }
    var modifiers = new java.util.ArrayList<String>();
    for (int i = 1; i < parts.length; i++) {
      modifiers.add(parts[i].trim().toLowerCase(Locale.ROOT));
    }
    return new SigmaField(name, java.util.List.copyOf(modifiers));
  }

  public boolean isEqualsLike() {
    if (modifiers.isEmpty()) return true; // default equals
    // 모든 modifier 가 equals-like 일 때만 true.
    for (var m : modifiers) {
      if (!EQUALS_LIKE.contains(m)) return false;
    }
    return true;
  }
}
