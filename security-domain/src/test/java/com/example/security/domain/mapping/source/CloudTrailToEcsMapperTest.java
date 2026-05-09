package com.example.security.domain.mapping.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.security.domain.common.Severity;
import com.example.security.domain.common.TenantId;
import com.example.security.domain.event.RawEvent;
import com.example.security.domain.mapping.EventNormalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloudTrailToEcsMapperTest {

  private final CloudTrailToEcsMapper mapper = new CloudTrailToEcsMapper();
  private final Instant receivedAt = Instant.parse("2026-05-09T12:00:00Z");

  /**
   * AWS CloudTrail user guide 의 ConsoleLogin 샘플 레코드 형태.
   *
   * <p>출처: <a href=
   * "https://docs.aws.amazon.com/awscloudtrail/latest/userguide/cloudtrail-event-reference-record-contents.html">
   * AWS Docs — CloudTrail event reference</a>
   */
  @Test
  void ConsoleLogin_성공_정규화() {
    Map<String, Object> userIdentity = new LinkedHashMap<>();
    userIdentity.put("type", "IAMUser");
    userIdentity.put("principalId", "AIDAJDPLRKLG7UEXAMPLE");
    userIdentity.put("arn", "arn:aws:iam::123456789012:user/Anaya");
    userIdentity.put("accountId", "123456789012");
    userIdentity.put("userName", "Anaya");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventVersion", "1.08");
    payload.put("eventTime", "2026-05-09T11:59:30Z");
    payload.put("eventSource", "signin.amazonaws.com");
    payload.put("eventName", "ConsoleLogin");
    payload.put("awsRegion", "us-east-1");
    payload.put("sourceIPAddress", "203.0.113.45");
    payload.put("userAgent", "Mozilla/5.0");
    payload.put("recipientAccountId", "123456789012");
    payload.put("eventID", "11111111-2222-3333-4444-555555555555");
    payload.put("eventType", "AwsConsoleSignIn");
    payload.put("userIdentity", userIdentity);
    Map<String, Object> responseElements = new LinkedHashMap<>();
    responseElements.put("ConsoleLogin", "Success");
    payload.put("responseElements", responseElements);

    var event = mapper.normalize(raw(payload));

    assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-05-09T11:59:30Z"));
    assertThat(event.eventAction()).isEqualTo("ConsoleLogin");
    assertThat(event.eventCategory()).isEqualTo("authentication");
    assertThat(event.eventOutcome()).isEqualTo("success");
    assertThat(event.eventType()).isEqualTo("allowed");
    assertThat(event.userName()).isEqualTo("Anaya");
    assertThat(event.sourceIp()).isEqualTo("203.0.113.45");
    assertThat(event.severity()).isEqualTo(Severity.LOW);
    assertThat(event.labels())
        .containsEntry("event.provider", "signin.amazonaws.com")
        .containsEntry("user.id", "arn:aws:iam::123456789012:user/Anaya")
        .containsEntry("cloud.provider", "aws")
        .containsEntry("cloud.region", "us-east-1")
        .containsEntry("cloud.account.id", "123456789012")
        .containsEntry("aws.cloudtrail.user_identity.type", "IAMUser");
    assertThat(event.eventId().toString()).isEqualTo("11111111-2222-3333-4444-555555555555");
    assertThat(event.message()).contains("ConsoleLogin").contains("Anaya").contains("success");
  }

  @Test
  void errorCode_있으면_failure_와_HIGH_severity() {
    Map<String, Object> userIdentity = new LinkedHashMap<>();
    userIdentity.put("type", "IAMUser");
    userIdentity.put("arn", "arn:aws:iam::123456789012:user/Bob");
    userIdentity.put("userName", "Bob");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventTime", "2026-05-09T11:00:00Z");
    payload.put("eventSource", "signin.amazonaws.com");
    payload.put("eventName", "ConsoleLogin");
    payload.put("awsRegion", "ap-northeast-2");
    payload.put("sourceIPAddress", "10.0.1.5");
    payload.put("userIdentity", userIdentity);
    payload.put("errorCode", "Failure");
    payload.put("errorMessage", "Failed authentication");
    payload.put("eventID", "deadbeef-dead-beef-dead-beefdeadbeef");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventOutcome()).isEqualTo("failure");
    assertThat(event.eventType()).isEqualTo("denied");
    assertThat(event.severity()).isEqualTo(Severity.HIGH);
    assertThat(event.labels()).containsEntry("error.code", "Failure").containsEntry("error.message", "Failed authentication");
  }

  @Test
  void AssumeRole_도_authentication_카테고리() {
    Map<String, Object> userIdentity = new LinkedHashMap<>();
    userIdentity.put("type", "AssumedRole");
    userIdentity.put("principalId", "AROACLKWSDQRAOEXAMPLE:my-session");
    userIdentity.put("arn", "arn:aws:sts::123456789012:assumed-role/my-role/my-session");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventTime", "2026-05-09T10:30:00Z");
    payload.put("eventSource", "sts.amazonaws.com");
    payload.put("eventName", "AssumeRole");
    payload.put("awsRegion", "us-west-2");
    payload.put("sourceIPAddress", "172.16.0.10");
    payload.put("userIdentity", userIdentity);
    payload.put("eventID", "abc12345-1111-2222-3333-444455556666");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventCategory()).isEqualTo("authentication");
    assertThat(event.eventAction()).isEqualTo("AssumeRole");
    assertThat(event.eventOutcome()).isEqualTo("success");
    // AssumedRole 의 경우 userName 이 없으므로 principalId 로 fallback.
    assertThat(event.userName()).isEqualTo("AROACLKWSDQRAOEXAMPLE:my-session");
  }

  @Test
  void create_for_관리_API_는_configuration_카테고리() {
    Map<String, Object> userIdentity = new LinkedHashMap<>();
    userIdentity.put("type", "IAMUser");
    userIdentity.put("userName", "ops");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventTime", "2026-05-09T09:00:00Z");
    payload.put("eventSource", "ec2.amazonaws.com");
    payload.put("eventName", "CreateSecurityGroup");
    payload.put("awsRegion", "us-east-1");
    payload.put("sourceIPAddress", "198.51.100.1");
    payload.put("userIdentity", userIdentity);
    payload.put("eventID", "11111111-1111-1111-1111-111111111111");

    var event = mapper.normalize(raw(payload));

    assertThat(event.eventCategory()).isEqualTo("configuration");
    assertThat(event.severity()).isEqualTo(Severity.INFO);
  }

  @Test
  void requestParameters_는_event_original_에_보존() {
    Map<String, Object> userIdentity = new LinkedHashMap<>();
    userIdentity.put("userName", "Carol");

    Map<String, Object> requestParameters = new LinkedHashMap<>();
    requestParameters.put("bucketName", "my-bucket");
    requestParameters.put("Host", "my-bucket.s3.amazonaws.com");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventTime", "2026-05-09T12:00:00Z");
    payload.put("eventName", "GetObject");
    payload.put("eventSource", "s3.amazonaws.com");
    payload.put("awsRegion", "us-east-1");
    payload.put("sourceIPAddress", "10.0.0.1");
    payload.put("userIdentity", userIdentity);
    payload.put("requestParameters", requestParameters);
    payload.put("eventID", "22222222-2222-2222-2222-222222222222");

    var event = mapper.normalize(raw(payload));

    assertThat(event.labels()).containsKey("event.original.request_parameters");
    assertThat(event.labels().get("event.original.request_parameters")).contains("bucketName=my-bucket");
  }

  @Test
  void eventID_없으면_랜덤_UUID() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventTime", "2026-05-09T12:00:00Z");
    payload.put("eventName", "DescribeInstances");
    payload.put("userIdentity", Map.of("userName", "x"));
    var event = mapper.normalize(raw(payload));
    assertThat(event.eventId()).isNotNull();
  }

  @Test
  void schema_불일치_거부() {
    var raw =
        new RawEvent(
            TenantId.of("acme"), receivedAt, "aws", "ecs", Map.of("eventName", "x"));
    assertThatThrownBy(() -> mapper.normalize(raw))
        .isInstanceOf(EventNormalizer.UnsupportedSchemaException.class);
  }

  private RawEvent raw(Map<String, Object> payload) {
    return new RawEvent(TenantId.of("acme"), receivedAt, "aws", CloudTrailToEcsMapper.SCHEMA, payload);
  }
}
