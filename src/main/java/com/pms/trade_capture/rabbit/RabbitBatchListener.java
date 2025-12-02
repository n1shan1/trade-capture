package com.pms.trade_capture.rabbit;


import com.google.protobuf.InvalidProtocolBufferException;

import com.pms.trade_capture.config.RabbitConfig;
import com.pms.trade_capture.domain.DlqEntry;
import com.pms.trade_capture.dto.TradeEventDto;
import com.pms.trade_capture.dto.TradeProtoMapper;
import com.pms.trade_capture.proto.TradeEventProto;
import com.pms.trade_capture.repository.DlqRepository;
import com.pms.trade_capture.service.TransactionalIngestService;
import com.pms.trade_capture.util.AppMetrics;
import com.pms.trade_capture.util.JsonUtil;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.springframework.amqp.support.AmqpHeaders;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RabbitBatchListener {

    private final TransactionalIngestService ingestService;
    private final DlqRepository dlqRepository;
    private final AppMetrics metrics;

    public RabbitBatchListener(TransactionalIngestService ingestService, DlqRepository dlqRepository, AppMetrics metrics) {
        this.ingestService = ingestService;
        this.dlqRepository = dlqRepository;
        this.metrics = metrics;
    }

    // Manual ack mode: container factory should use MANUAL
    @RabbitListener(queues = RabbitConfig.INGEST_QUEUE, concurrency = "2-5")
    public void onMessage(Message message, Channel channel, @Headers Map<String, Object> headers) throws IOException {
        long deliveryTag = (long) headers.get(AmqpHeaders.DELIVERY_TAG);

        // For simplicity this listener expects each message contains one TradeEvent JSON or Protobuf bytes,
        // but we also support batch payload: JSON array of TradeEventDto (if sender batches)
        try {
            byte[] body = message.getBody();

            // Try parse as Protobuf TradeEventProto
            try {
                TradeEventProto proto = TradeEventProto.parseFrom(body);
                // single event
                TradeEventDto dto = fromProto(proto);
                List<TradeEventDto> events = new ArrayList<>();
                events.add(dto);
                List<byte[]> payloads = new ArrayList<>();
                payloads.add(proto.toByteArray()); // store proto bytes in outbox
                ingestService.writeBatch(events, payloads);
            } catch (InvalidProtocolBufferException ex) {
                // fallback: try parse as JSON array or single JSON
                String txt = new String(body);
                List<TradeEventDto> events = JsonUtil.fromJsonArrayOrSingle(txt);
                List<byte[]> payloads = new ArrayList<>();
                for (TradeEventDto e : events) {
                    // map DTO -> proto
                    TradeEventProto p = TradeProtoMapper.toProto(e);
                    payloads.add(p.toByteArray());
                }
                ingestService.writeBatch(events, payloads);
            }

            // If we reach here: DB transaction succeeded -> acknowledge the incoming Rabbit message
            channel.basicAck(deliveryTag, false);
            metrics.incrementIngestSuccess();

        } catch (Exception e) {
            // Everything failed — write to DLQ and NACK (requeue or not per strategy)
            try {
                dlqRepository.save(new DlqEntry(new String(message.getBody()), e.getMessage()));
            } catch (Exception ignore) {}
            // NACK: requeue = true to allow retry; consider dead-letter after attempts
            channel.basicNack(deliveryTag, false, true);
            metrics.incrementIngestFail();
        }
    }

    private TradeEventDto fromProto(TradeEventProto proto) {
        TradeEventDto dto = new TradeEventDto();
        dto.portfolioId = java.util.UUID.fromString(proto.getPortfolioId());
        dto.tradeId = java.util.UUID.fromString(proto.getTradeId());
        dto.symbol = proto.getSymbol();
        dto.side = proto.getSide();
        dto.pricePerStock = proto.getPricePerStock();
        dto.quantity = proto.getQuantity();
        dto.timestamp = Instant.ofEpochSecond(proto.getTimestamp().getSeconds(), proto.getTimestamp().getNanos());
        return dto;
    }
}

