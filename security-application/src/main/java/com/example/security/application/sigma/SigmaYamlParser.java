package com.example.security.application.sigma;

import com.example.security.domain.sigma.SigmaRule;
import java.io.Reader;
import java.io.StringReader;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Sigma YAML 파서 — SnakeYAML 의 SafeConstructor 만 허용 (RCE 방지).
 *
 * <p>1개 YAML 문서 또는 multi-document YAML ({@code ---} 로 구분된 N 개) 양쪽 지원.
 */
public class SigmaYamlParser {

  private final Clock clock;

  public SigmaYamlParser(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
  }

  public SigmaRule parseSingle(String yaml) {
    var loader = new LoaderOptions();
    loader.setMaxAliasesForCollections(50);
    var snake = new Yaml(new SafeConstructor(loader));
    Object obj = snake.load(new StringReader(yaml));
    if (!(obj instanceof Map<?, ?> root)) {
      throw new IllegalArgumentException("Sigma YAML 의 root 가 map 이 아님");
    }
    return toSigmaRule(root, yaml);
  }

  /** multi-document YAML 또는 단일 YAML 모두 받아서 list 로 반환. */
  public List<SigmaRule> parseAll(String yaml) {
    var loader = new LoaderOptions();
    loader.setMaxAliasesForCollections(50);
    var snake = new Yaml(new SafeConstructor(loader));
    var result = new ArrayList<SigmaRule>();
    Reader reader = new StringReader(yaml);
    int idx = 0;
    for (Object obj : snake.loadAll(reader)) {
      idx++;
      if (obj == null) continue;
      if (!(obj instanceof Map<?, ?> root)) {
        throw new IllegalArgumentException("Sigma YAML 문서 " + idx + " 의 root 가 map 이 아님");
      }
      // multi-document 인 경우 source 를 통째로 보관 — 정확한 분리는 cost 대비 효익 낮음.
      result.add(toSigmaRule(root, yaml));
    }
    return List.copyOf(result);
  }

  @SuppressWarnings("unchecked")
  private SigmaRule toSigmaRule(Map<?, ?> root, String source) {
    var rawId = root.get("id");
    var id = rawId == null ? java.util.UUID.randomUUID().toString() : rawId.toString();
    var title = requireString(root.get("title"), "title");
    var description = stringOrNull(root.get("description"));
    var status = stringOrNull(root.get("status"));
    var level = stringOrNull(root.get("level"));
    var author = stringOrNull(root.get("author"));
    var refs = stringList(root.get("references"));
    var tags = stringList(root.get("tags"));
    var falsepositives = stringList(root.get("falsepositives"));
    var fields = stringList(root.get("fields"));

    var logsource = new LinkedHashMap<String, String>();
    if (root.get("logsource") instanceof Map<?, ?> ls) {
      ls.forEach(
          (k, v) -> {
            if (k != null && v != null) logsource.put(k.toString(), v.toString());
          });
    }

    Map<String, Object> detection;
    if (root.get("detection") instanceof Map<?, ?> det) {
      detection = (Map<String, Object>) (Map<?, ?>) det;
    } else {
      throw new IllegalArgumentException("Sigma 룰 " + title + " 에 detection 이 없음");
    }

    return new SigmaRule(
        id,
        title,
        description,
        status,
        level,
        author,
        refs,
        tags,
        falsepositives,
        Map.copyOf(logsource),
        detection,
        fields,
        source,
        clock.instant());
  }

  private static String requireString(Object v, String key) {
    if (v == null) {
      throw new IllegalArgumentException("Sigma 룰 필수 필드 누락: " + key);
    }
    return v.toString();
  }

  private static String stringOrNull(Object v) {
    return v == null ? null : v.toString();
  }

  private static List<String> stringList(Object v) {
    if (v == null) return List.of();
    if (v instanceof List<?> list) {
      var out = new ArrayList<String>(list.size());
      for (var item : list) {
        if (item != null) out.add(item.toString());
      }
      return List.copyOf(out);
    }
    return List.of(v.toString());
  }
}
