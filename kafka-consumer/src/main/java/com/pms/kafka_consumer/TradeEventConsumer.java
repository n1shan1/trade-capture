package com.pms.kafka_consumer;

import com.pms.kafka_consumer.proto.TradeEventProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeTradeEvent(@Payload TradeEventProto tradeEvent,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  Acknowledgment acknowledgment) {

        log.info("Received trade event: portfolioId={}, tradeId={}, symbol={}, side={}, price={}, quantity={}, offset={}, partition={}",
                tradeEvent.getPortfolioId(), tradeEvent.getTradeId(), tradeEvent.getSymbol(),
                tradeEvent.getSide(), tradeEvent.getPricePerStock(), tradeEvent.getQuantity(),
                offset, partition);

        // Acknowledge the message
        acknowledgment.acknowledge();
    }
}