package com.example.security.streaming.job

import com.example.security.domain.event.LogEvent
import com.example.security.domain.rule.AlertRule
import com.example.security.streaming.operator.CorrelationProcessFunction
import com.example.security.streaming.serde.EventJsonDeserializer
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.serialization.SerializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

/**
 * Flink 메인 진입점. `events.normalized` 를 source 로, 룰 broadcast 와 함께
 * KeyedBroadcastProcessFunction 으로 평가하고 결과를 `alerts.fired` 로 sink.
 *
 * 실행:
 *
 * ```
 * flink run -c com.example.security.streaming.job.AlertCorrelationJob \
 *   security-streaming/build/libs/security-streaming-0.1.0.jar \
 *   --bootstrap kafka:9092 \
 *   --events-topic events.normalized \
 *   --rules-topic alert-rules.broadcast \
 *   --alerts-topic alerts.fired
 * ```
 *
 * 운영 ENV 의 룰 broadcast 는 보통 별도 reader (Postgres → Kafka producer) 가 룰 변경 시
 * Kafka topic 에 publish 하는 형태. 본 job 자체는 변경 채널만 알 뿐이다.
 */
object AlertCorrelationJob {

    @JvmStatic
    @Throws(Exception::class)
    fun main(args: Array<String>) {
        val params = ParameterTool.fromArgs(args)
        val bootstrap = params.get("bootstrap", "localhost:9092")
        val eventsTopic = params.get("events-topic", "events.normalized")
        val rulesTopic = params.get("rules-topic", "alert-rules.broadcast")
        val alertsTopic = params.get("alerts-topic", "alerts.fired")

        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.config.globalJobParameters = params

        val eventsSource =
            KafkaSource.builder<LogEvent>()
                .setBootstrapServers(bootstrap)
                .setTopics(eventsTopic)
                .setGroupId("security-streaming-events")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(EventDeserializationSchema())
                .build()

        val rulesSource =
            KafkaSource.builder<AlertRule>()
                .setBootstrapServers(bootstrap)
                .setTopics(rulesTopic)
                .setGroupId("security-streaming-rules-" + System.nanoTime())
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(RuleDeserializationSchema())
                .build()

        val events =
            env.fromSource(eventsSource, WatermarkStrategy.forMonotonousTimestamps(), "events.normalized")

        val rules =
            env.fromSource(rulesSource, WatermarkStrategy.noWatermarks(), "alert-rules")
                .broadcast(CorrelationProcessFunction.RULES_DESCRIPTOR)

        // 본 job 의 핵심 — 룰 broadcast 와 keyed event stream 을 연결한 뒤 평가.
        // keyBy 시점에는 룰 / 그룹 키를 알 수 없다 (룰은 broadcast 채널로만 들어옴) → tenantId 로만
        // keyBy 하고, CorrelationProcessFunction 이 broadcast 룰 전체를 순회하며 룰별 그룹 키 단위로
        // RuleEvaluator state 를 fan-out 한다. 룰이 수만 개로 늘면 keyed 룰 분산 검토 (ADR-0008).
        val alertJsonStream =
            events
                .keyBy { e -> e.tenantId.value }
                .connect(rules)
                .process(CorrelationProcessFunction())

        val sink =
            KafkaSink.builder<String>()
                .setBootstrapServers(bootstrap)
                .setRecordSerializer(
                    KafkaRecordSerializationSchema.builder<String>()
                        .setTopic(alertsTopic)
                        .setValueSerializationSchema(SimpleStringSerializer())
                        .build(),
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build()

        alertJsonStream.sinkTo(sink)

        env.execute("alert-correlation-job")
    }

    class EventDeserializationSchema : DeserializationSchema<LogEvent> {
        @Transient
        private var deserializer: EventJsonDeserializer? = null

        @Throws(IOException::class)
        override fun deserialize(message: ByteArray): LogEvent {
            if (deserializer == null) deserializer = EventJsonDeserializer()
            try {
                return deserializer!!.deserialize(message)
            } catch (e: Exception) {
                throw IOException("event deserialization failed", e)
            }
        }

        override fun isEndOfStream(next: LogEvent): Boolean = false

        override fun getProducedType(): TypeInformation<LogEvent> =
            TypeInformation.of(LogEvent::class.java)
    }

    class RuleDeserializationSchema : DeserializationSchema<AlertRule> {
        @Throws(IOException::class)
        override fun deserialize(message: ByteArray): AlertRule {
            throw UnsupportedOperationException(
                "운영 환경에서 별도 RuleJsonDeserializer 주입 — 본 job 은 broadcast 인터페이스만 정의.",
            )
        }

        override fun isEndOfStream(next: AlertRule): Boolean = false

        override fun getProducedType(): TypeInformation<AlertRule> =
            TypeInformation.of(AlertRule::class.java)
    }

    class SimpleStringSerializer : SerializationSchema<String> {
        override fun serialize(element: String): ByteArray =
            element.toByteArray(StandardCharsets.UTF_8)
    }
}
