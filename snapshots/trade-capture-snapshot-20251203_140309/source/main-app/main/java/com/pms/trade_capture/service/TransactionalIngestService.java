// package com.pms.trade_capture.service;

// import java.util.List;
// import java.util.stream.Collectors;
// import java.util.stream.IntStream;

// import org.springframework.stereotype.Service;

// import com.pms.trade_capture.domain.OutboxEvent;
// import com.pms.trade_capture.domain.SafeStoreTrade;
// import com.pms.trade_capture.dto.TradeEventDto;
// import com.pms.trade_capture.dto.TradeEventMapper;
// import com.pms.trade_capture.repository.OutboxRepository;
// import com.pms.trade_capture.repository.SafeStoreRepository;

// import jakarta.transaction.Transactional;

// @Service
// public class TransactionalIngestService {

//     private final SafeStoreRepository safeRepo;
//     private final OutboxRepository outboxRepo;

//     public TransactionalIngestService(SafeStoreRepository safeRepo, OutboxRepository outboxRepo) {
//         this.safeRepo = safeRepo;
//         this.outboxRepo = outboxRepo;
//     }

//     @Transactional
//     public void writeBatch(List<TradeEventDto> events, List<byte[]> protobufPayloads) {
//         // events and payloads must be same size and aligned
//         List<SafeStoreTrade> safeTrades = events.stream()
//                 .map(TradeEventMapper::toSafeStoreTrade)
//                 .collect(Collectors.toList());

//         List<OutboxEvent> outboxEvents = IntStream.range(0, events.size())
//                 .mapToObj(i -> TradeEventMapper.toOutboxEvent(events.get(i), protobufPayloads.get(i)))
//                 .collect(Collectors.toList());

//         safeRepo.saveAll(safeTrades);
//         outboxRepo.saveAll(outboxEvents);
//     }
// }

