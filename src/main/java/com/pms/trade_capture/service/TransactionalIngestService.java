package com.pms.trade_capture.service;

import com.pms.trade_capture.domain.OutboxEvent;
import com.pms.trade_capture.domain.SafeStoreTrade;
import com.pms.trade_capture.dto.TradeEventDto;
import com.pms.trade_capture.repository.OutboxRepository;
import com.pms.trade_capture.repository.SafeStoreRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionalIngestService {

    private final SafeStoreRepository safeRepo;
    private final OutboxRepository outboxRepo;

    public TransactionalIngestService(SafeStoreRepository safeRepo, OutboxRepository outboxRepo) {
        this.safeRepo = safeRepo;
        this.outboxRepo = outboxRepo;
    }

    @Transactional
    public void writeBatch(List<TradeEventDto> events, List<byte[]> protobufPayloads) {
        // events and payloads must be same size and aligned
        for (int i = 0; i < events.size(); i++) {
            TradeEventDto e = events.get(i);
            SafeStoreTrade ss = new SafeStoreTrade(
                    e.portfolioId, e.tradeId, e.symbol, e.side,
                    e.pricePerStock, e.quantity, LocalDateTime.ofInstant(e.timestamp, java.time.ZoneOffset.UTC)
            );
            safeRepo.save(ss);

            OutboxEvent oe = new OutboxEvent(e.portfolioId, e.tradeId, protobufPayloads.get(i));
            outboxRepo.save(oe);
        }
    }
}

