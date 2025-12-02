package com.pms.trade_capture.config;

import org.springframework.context.annotation.Bean;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    // RabbitMQ Configuration Constants
    public static final String INGEST_QUEUE = "trade.ingest.raw";
    public static final String INGEST_EXCHANGE = "trade.ingest.exchange";
    public static final String INGEST_DLQ = "trade.ingest.dlq";

    // Bean Definitions
    @Bean
    public Exchange ingestExchange() {
        // Direct Exchange for Ingest
        return ExchangeBuilder.directExchange(INGEST_EXCHANGE).durable(true).build();
    }

    // Ingest Queue with Dead Letter Queue configuration
    @Bean
    public Queue ingestQueue() {
        // Durable Queue with DLQ settings
        return QueueBuilder.durable(INGEST_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", INGEST_DLQ)
                .build();
    }

    // Dead Letter Queue
    @Bean
    public Queue dlq() {
        return QueueBuilder.durable(INGEST_DLQ).build();
    }

    // Binding between Ingest Queue and Ingest Exchange
    @Bean
    public Binding binding(Queue ingestQueue, Exchange ingestExchange) {
        return BindingBuilder.bind(ingestQueue).to(ingestExchange).with(INGEST_QUEUE).noargs();
    }
}
