package com.example.security.domain.mapping.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class K8sAuditToEcsMapperTest {

  private final K8sAuditToEcsMapper mapper = new K8sAuditToEcsMapper();
  private final Instant receivedAt = Instant.parse("2026-05-09T12:00:00Z");

  /**
   * Kubernetes audit log 의 표준 샘플 — Pod create, ResponseComplete stage.
   *
   * <p>출처: <a href="https://kubernetes.io/docs/tasks/debug/debug-cluster/audit/">Kubernetes
   * Documentation — Audit Log</a>
   */
  @Test
  void Pod_create_성공_정규화() {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("username", "kubernetes-admin");
    user.put("uid", "admin-uid-1");
    user.put("groups", List.of("system:masters", "system:authenticated"));

    Map<String, Object> objectRef = new LinkedHashMap<>();
    objectRef.put("resource", "pods");
    objectRef.put("namespace", "production");
    objectRef.put("apiVersion", "v1");
    objectRef.put("name", "my-app-12345");

    Map<String, Object> responseStatus = new LinkedHashMap<>();
    responseStatus.put("metadata", Map.of());
    responseStatus.put("code", 201);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("kind", "Event");
    payload.put("apiVersion", "audit.k8s.io/v1");
    payload.put("level", "RequestResponse");
    payload.put("auditID", "0d4fac6f-0a0a-4f9c-93e8-0b9f0e0a0b0c");
    payload.put("stage", "ResponseComplete");
    payload.put("requestURI", "/api/v1/namespaces/production/pods");
    payload.put("verb", "create");
    payload.put("user", user);
    payload.put("sourceIPs", List.of("10.0.1.1", "10.0.1.2"));
    payload.put("objectRef", objectRef);
    payload.put("responseStatus", responseStatus);
    payload.put("requestReceivedTimestamp", "2026-05-09T11:59:00.000000Z");
    payload.put("stageTimestamp", "2026-05-09T11:59:00.123456Z");

    var event = mapper.normalize(raw(payload));

    assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-05-09T11:59:00.000000Z"));
    assertThat(event.eventAction()).isEqualTo("create");
    assertThat(event.eventCategory()).isEqualTo("configuration");
    assertThat(event.eventOutcome()).isEqualTo("success");
    assertThat(event.eventType()).isEqualTo("allowed");
    assertThat(event.userName()).isEqualTo("kubernetes-admin");
    assertThat(event.sourceIp()).isEqualTo("10.0.1.1"); // 첫 번째만
    assertThat(event.severity()).isEqualTo(Severity.LOW); // create 성공 → LOW
    assertThat(event.labels())
        .containsEntry("kubernetes.namespace", "production")
        .containsEntry("kubernetes.resource", "pods")
        .containsEntry("kubernetes.object_name", "my-app-12345")
        .containsEntry("kubernetes.audit.stage", "ResponseComplete")
        .containsEntry("user.id", "admin-uid-1")
        .containsEntry("user.groups", "system:masters,system:authenticated")
        .containsEntry("http.response.status_code", "201");
    assertThat(event.eventId().toString()).isEqualTo("0d4fac6f-0a0a-4f9c-93e8-0b9f0e0a0b0c");
  }

  @Test
  void delete_도_configuration_과_MEDIUM_severity() {
    Map<String, Object> objectRef = new LinkedHashMap<>();
    objectRef.put("resource", "deployments");
    objectRef.put("namespace", "default");
    objectRef.put("name", "critical-app");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("auditID", "ddddddd1-2222-3333-4444-555555555555");
    payload.put("stage", "ResponseComplete");
    payload.put("verb", "delete");
    payload.put("user", Map.of("username", "intern", "uid", "u1"));
    payload.put("sourceIPs", List.of("172.16.0.5"));
    payload.put("objectRef", objectRef);
    payload.put("responseStatus", Map.of("code", 200));
    payload.put("requestReceivedTimestamp", "2026-05-09T11:00:00Z");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventAction()).isEqualTo("delete");
    assertThat(event.eventCategory()).isEqualTo("configuration");
    assertThat(event.eventOutcome()).isEqualTo("success");
    assertThat(event.severity()).isEqualTo(Severity.MEDIUM); // delete 성공 → MEDIUM
  }

  @Test
  void responseStatus_403_은_failure_HIGH() {
    Map<String, Object> objectRef = new LinkedHashMap<>();
    objectRef.put("resource", "secrets");
    objectRef.put("namespace", "kube-system");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("auditID", "1111aaaa-2222-bbbb-3333-cccccccccccc");
    payload.put("verb", "get");
    payload.put("user", Map.of("username", "system:anonymous"));
    payload.put("sourceIPs", List.of("198.51.100.99"));
    payload.put("objectRef", objectRef);
    payload.put("responseStatus", Map.of("code", 403, "reason", "Forbidden"));
    payload.put("requestReceivedTimestamp", "2026-05-09T11:30:00Z");
    payload.put("requestURI", "/api/v1/namespaces/kube-system/secrets");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventOutcome()).isEqualTo("failure");
    assertThat(event.eventType()).isEqualTo("denied");
    assertThat(event.severity()).isEqualTo(Severity.HIGH);
    assertThat(event.labels()).containsEntry("http.response.status_code", "403");
  }

  @Test
  void responseStatus_500_은_failure_MEDIUM() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("auditID", "1111aaaa-2222-bbbb-3333-eeeeeeeeeeee");
    payload.put("verb", "list");
    payload.put("user", Map.of("username", "kube-controller"));
    payload.put("sourceIPs", List.of("10.0.0.1"));
    payload.put("objectRef", Map.of("resource", "nodes"));
    payload.put("responseStatus", Map.of("code", 500));
    payload.put("requestReceivedTimestamp", "2026-05-09T11:30:00Z");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventOutcome()).isEqualTo("failure");
    assertThat(event.severity()).isEqualTo(Severity.MEDIUM);
  }

  @Test
  void TokenReview_은_authentication_카테고리() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("auditID", "tttttttttt11-2222-3333-4444-555555555555");
    payload.put("verb", "create");
    payload.put("requestURI", "/apis/authentication.k8s.io/v1/tokenreviews");
    payload.put("user", Map.of("username", "kubelet"));
    payload.put("sourceIPs", List.of("10.0.0.10"));
    payload.put("responseStatus", Map.of("code", 201));
    payload.put("requestReceivedTimestamp", "2026-05-09T11:30:00Z");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventCategory()).isEqualTo("authentication");
    assertThat(event.eventAction()).isEqualTo("create");
  }

  @Test
  void requestReceivedTimestamp_없으면_stageTimestamp_사용() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("auditID", "fffffff1-2222-3333-4444-555555555555");
    payload.put("verb", "get");
    payload.put("user", Map.of("username", "x"));
    payload.put("sourceIPs", List.of("10.0.0.1"));
    payload.put("responseStatus", Map.of("code", 200));
    payload.put("stageTimestamp", "2026-05-09T10:00:00.999Z");

    var event = mapper.normalize(raw(payload));

    assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-05-09T10:00:00.999Z"));
  }

  @Test
  void schema_불일치_거부() {
    var raw =
        new RawEvent(TenantId.of("acme"), receivedAt, "k8s", "ecs", Map.of("verb", "get"));
    assertThatThrownBy(() -> mapper.normalize(raw))
        .isInstanceOf(EventNormalizer.UnsupportedSchemaException.class);
  }

  private RawEvent raw(Map<String, Object> payload) {
    return new RawEvent(TenantId.of("acme"), receivedAt, "k8s", K8sAuditToEcsMapper.SCHEMA, payload);
  }
}
