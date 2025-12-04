package com.pms.trade_simulator;

import com.google.protobuf.Timestamp;
import com.pms.trade_simulator.proto.TradeEventProto;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.Producer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TradeSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(TradeSimulatorService.class);

    private final Producer producer;

    @Value("${app.simulator.rate-per-minute:6}")
    private int ratePerMinute;

    @Value("${app.simulator.run-indefinitely:true}")
    private boolean runIndefinitely;

    private final Random random = new Random();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public TradeSimulatorService(Producer producer) {
        this.producer = producer;
    }

    @PostConstruct
    public void startSimulation() {
        long intervalMillis = (60 * 1000) / ratePerMinute; // Convert rate per minute to interval in milliseconds
        log.info("Starting trade simulation: {} msg/min (every {} ms), run indefinitely: {}", 
                 ratePerMinute, intervalMillis, runIndefinitely);

        if (runIndefinitely) {
            // Schedule periodic execution indefinitely
            executor.scheduleAtFixedRate(() -> sendTradeEvent(), 0, intervalMillis, TimeUnit.MILLISECONDS);
        } else {
            // Legacy behavior: run for fixed duration
            int totalMessages = ratePerMinute;
            for (int i = 0; i < totalMessages; i++) {
                executor.schedule(() -> sendTradeEvent(), i * intervalMillis, TimeUnit.MILLISECONDS);
            }
            executor.schedule(() -> {
                log.info("Simulation completed");
                executor.shutdown();
                System.exit(0);
            }, (totalMessages * intervalMillis) / 1000, TimeUnit.SECONDS);
        }
    }

    private void sendTradeEvent() {
        TradeEventProto event = generateRandomTradeEvent();
        log.info("Sending trade event: portfolioId={}, tradeId={}, symbol={}, side={}, price={}, quantity={}",
                event.getPortfolioId(), event.getTradeId(), event.getSymbol(), event.getSide(),
                event.getPricePerStock(), event.getQuantity());
        Message message = producer.messageBuilder().addData(event.toByteArray()).build();
        producer.send(message, confirmation -> {
            if (confirmation.isConfirmed()) {
                log.info("Message sent successfully: tradeId={}", event.getTradeId());
            } else {
                log.error("Message failed to send: tradeId={}", event.getTradeId());
            }
        });
    }

    private TradeEventProto generateRandomTradeEvent() {
        String[] symbols = { "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX" };
        String[] sides = { "BUY", "SELL" };

        UUID portfolioId = UUID.randomUUID();
        UUID tradeId = UUID.randomUUID();
        String symbol = symbols[random.nextInt(symbols.length)];
        String side = sides[random.nextInt(sides.length)];
        double price = 100 + random.nextDouble() * 900; // 100-1000
        long quantity = 1 + random.nextInt(1000); // 1-1000
        Instant timestamp = Instant.now();

        Timestamp ts = Timestamp.newBuilder().setSeconds(timestamp.getEpochSecond()).setNanos(timestamp.getNano()).build();

        return TradeEventProto.newBuilder()
                .setPortfolioId(portfolioId.toString())
                .setTradeId(tradeId.toString())
                .setSymbol(symbol)
                .setSide(side)
                .setPricePerStock(price)
                .setQuantity(quantity)
                .setTimestamp(ts)
                .build();
    }
}