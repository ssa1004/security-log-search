package com.example.security.streaming.job;

import com.example.security.domain.event.LogEvent;
import com.example.security.domain.rule.AlertRule;
import com.example.security.streaming.operator.CorrelationProcessFunction;
import com.example.security.streaming.serde.EventJsonDeserializer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Flink 메인 진입점. {@code events.normalized} 를 source 로, 룰 broadcast 와 함께
 * KeyedBroadcastProcessFunction 으로 평가하고 결과를 {@code alerts.fired} 로 sink.
 *
 * <p>실행:
 *
 * <pre>
 * flink run -c com.example.security.streaming.job.AlertCorrelationJob \
 *   security-streaming/build/libs/security-streaming-0.1.0.jar \
 *   --bootstrap kafka:9092 \
 *   --events-topic events.normalized \
 *   --rules-topic alert-rules.broadcast \
 *   --alerts-topic alerts.fired
 * </pre>
 *
 * <p>운영 ENV 의 룰 broadcast 는 보통 별도 reader (Postgres → Kafka producer) 가 룰 변경 시
 * Kafka topic 에 publish 하는 형태. 본 job 자체는 변경 채널만 알 뿐이다.
 */
public class AlertCorrelationJob {

  public static void main(String[] args) throws Exception {
    var params = ParameterTool.fromArgs(args);
    var bootstrap = params.get("bootstrap", "localhost:9092");
    var eventsTopic = params.get("events-topic", "events.normalized");
    var rulesTopic = params.get("rules-topic", "alert-rules.broadcast");
    var alertsTopic = params.get("alerts-topic", "alerts.fired");

    var env = StreamExecutionEnvironment.getExecutionEnvironment();
    env.getConfig().setGlobalJobParameters(params);

    var eventsSource =
        KafkaSource.<LogEvent>builder()
            .setBootstrapServers(bootstrap)
            .setTopics(eventsTopic)
            .setGroupId("security-streaming-events")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new EventDeserializationSchema())
            .build();

    var rulesSource =
        KafkaSource.<AlertRule>builder()
            .setBootstrapServers(bootstrap)
            .setTopics(rulesTopic)
            .setGroupId("security-streaming-rules-" + System.nanoTime())
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new RuleDeserializationSchema())
            .build();

    DataStream<LogEvent> events =
        env.fromSource(eventsSource, WatermarkStrategy.forMonotonousTimestamps(), "events.normalized");

    BroadcastStream<AlertRule> rules =
        env.fromSource(rulesSource, WatermarkStrategy.noWatermarks(), "alert-rules")
            .broadcast(CorrelationProcessFunction.RULES_DESCRIPTOR);

    // 본 job 의 핵심 — 룰 broadcast 와 keyed event stream 을 연결한 뒤 평가.
    // keyBy 시점에는 룰 / 그룹 키를 알 수 없다 (룰은 broadcast 채널로만 들어옴) → tenantId 로만
    // keyBy 하고, CorrelationProcessFunction 이 broadcast 룰 전체를 순회하며 룰별 그룹 키 단위로
    // RuleEvaluator state 를 fan-out 한다. 룰이 수만 개로 늘면 keyed 룰 분산 검토 (ADR-0008).
    var alertJsonStream =
        events
            .keyBy(e -> e.tenantId().value())
            .connect(rules)
            .process(new CorrelationProcessFunction());

    var sink =
        KafkaSink.<String>builder()
            .setBootstrapServers(bootstrap)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder()
                    .setTopic(alertsTopic)
                    .setValueSerializationSchema(new SimpleStringSerializer())
                    .build())
            .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
            .build();

    alertJsonStream.sinkTo(sink);

    env.execute("alert-correlation-job");
  }

  static class EventDeserializationSchema implements DeserializationSchema<LogEvent> {
    private transient EventJsonDeserializer deserializer;

    @Override
    public LogEvent deserialize(byte[] message) throws java.io.IOException {
      if (deserializer == null) deserializer = new EventJsonDeserializer();
      try {
        return deserializer.deserialize(message);
      } catch (Exception e) {
        throw new java.io.IOException("event deserialization failed", e);
      }
    }

    @Override
    public boolean isEndOfStream(LogEvent next) {
      return false;
    }

    @Override
    public TypeInformation<LogEvent> getProducedType() {
      return TypeInformation.of(LogEvent.class);
    }
  }

  static class RuleDeserializationSchema implements DeserializationSchema<AlertRule> {
    @Override
    public AlertRule deserialize(byte[] message) throws java.io.IOException {
      throw new UnsupportedOperationException(
          "운영 환경에서 별도 RuleJsonDeserializer 주입 — 본 job 은 broadcast 인터페이스만 정의.");
    }

    @Override
    public boolean isEndOfStream(AlertRule next) {
      return false;
    }

    @Override
    public TypeInformation<AlertRule> getProducedType() {
      return TypeInformation.of(AlertRule.class);
    }
  }

  static class SimpleStringSerializer implements SerializationSchema<String> {
    @Override
    public byte[] serialize(String element) {
      return element.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}
