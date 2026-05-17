package com.example.security.bootstrap.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties

/**
 * Kafka 설정 — idempotent producer + tunable consumer.
 *
 * 핵심 (ADR-0009 backpressure):
 * - producer: enable.idempotence + acks=all + 압축
 * - consumer: max-poll-records 튜닝으로 backpressure 조절
 */
@Configuration
@EnableKafka
class KafkaConfig {

    @Bean
    fun producerFactory(
        @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") bootstrap: String,
    ): ProducerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrap
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        props[ProducerConfig.RETRIES_CONFIG] = Integer.MAX_VALUE
        props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        props[ProducerConfig.LINGER_MS_CONFIG] = 5
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(pf: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(pf)

    @Bean
    fun consumerFactory(
        @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") bootstrap: String,
        @Value("\${security.kafka.consumer.max-poll-records:200}") maxPollRecords: Int,
    ): ConsumerFactory<String, String> {
        val props: MutableMap<String, Any> = HashMap()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrap
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = maxPollRecords
        props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = 30_000
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        cf: ConsumerFactory<String, String>,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = cf
        factory.setConcurrency(1)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        return factory
    }
}
