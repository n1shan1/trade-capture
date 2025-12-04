package com.pms.trade_capture.config;

import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.pms.trade_capture.proto.TradeEventProto;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for publishing to Confluent Kafka with Schema Registry.
 * 
 * Uses KafkaProtobufSerializer to:
 * - Automatically register Protobuf schemas with Schema Registry
 * - Serialize TradeEventProto messages efficiently
 * - Enable schema evolution and compatibility checks
 * 
 * Producer is configured for maximum reliability:
 * - acks=all: Wait for all in-sync replicas to acknowledge
 * - enable.idempotence=true: Prevent duplicate messages
 * - max.in.flight.requests=1: Strict ordering guarantees
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Bean
    public ProducerFactory<String, TradeEventProto> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Kafka broker configuration
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        
        // Schema Registry configuration
        props.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        
        // Reliability and ordering configuration
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        
        // Performance tuning
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, TradeEventProto> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
