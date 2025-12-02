package com.pms.trade_capture.outbox;


import com.pms.trade_capture.domain.OutboxEvent;
import com.pms.trade_capture.proto.TradeEventProto;
import com.pms.trade_capture.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, TradeEventProto> kafkaTemplate;

    private final String topic = "raw-trades-proto";

    public OutboxDispatcher(OutboxRepository outboxRepo, KafkaTemplate<String, TradeEventProto> kafkaTemplate) {
        this.outboxRepo = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostConstruct
    public void start() {
        Thread t = new Thread(this::runLoop, "outbox-dispatcher");
        t.setDaemon(true);
        t.start();
    }

    private void runLoop() {
        while (true) {
            try {
                List<OutboxEvent> pending = outboxRepo.findPending(100);
                if (pending.isEmpty()) {
                    Thread.sleep(500);
                    continue;
                }
                for (OutboxEvent o : pending) {
                    try {
                        TradeEventProto proto = TradeEventProto.parseFrom(o.getPayload());
                        String key = o.getPortfolioId().toString();
                        kafkaTemplate.send(topic, key, proto).get(); // sync to ensure ordering + success
                        outboxRepo.markSent(o.getId());
                    } catch (Exception ex) {
                        log.error("Failed publish outbox {}", o.getId(), ex);
                        outboxRepo.incrementAttempts(o.getId());
                    }
                }
            } catch (Exception outer) {
                log.error("Outbox dispatch loop exception", outer);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }
}

