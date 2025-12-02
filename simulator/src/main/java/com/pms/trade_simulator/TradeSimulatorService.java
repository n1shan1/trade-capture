package com.pms.trade_simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${app.simulator.queue}")
    private String queueName;

    @Value("${app.simulator.rate:100}")
    private int ratePerSecond;

    @Value("${app.simulator.duration:60}")
    private int durationSeconds;

    private final Random random = new Random();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public TradeSimulatorService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void startSimulation() {
        log.info("Starting trade simulation: {} msg/sec for {} seconds", ratePerSecond, durationSeconds);

        long intervalMillis = 1000 / ratePerSecond;
        int totalMessages = ratePerSecond * durationSeconds;

        for (int i = 0; i < totalMessages; i++) {
            executor.schedule(() -> sendTradeEvent(), i * intervalMillis, TimeUnit.MILLISECONDS);
        }

        // Shutdown after duration
        executor.schedule(() -> {
            log.info("Simulation completed");
            executor.shutdown();
            System.exit(0);
        }, durationSeconds, TimeUnit.SECONDS);
    }

    private void sendTradeEvent() {
        TradeEventDto event = generateRandomTradeEvent();
        try {
            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(queueName, json);
            log.debug("Sent trade event: {}", event.getTradeId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize trade event", e);
        }
    }

    private TradeEventDto generateRandomTradeEvent() {
        String[] symbols = { "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX" };
        String[] sides = { "BUY", "SELL" };

        TradeEventDto dto = new TradeEventDto();
        dto.setPortfolioId(UUID.randomUUID());
        dto.setTradeId(UUID.randomUUID());
        dto.setSymbol(symbols[random.nextInt(symbols.length)]);
        dto.setSide(sides[random.nextInt(sides.length)]);
        dto.setPricePerStock(100 + random.nextDouble() * 900); // 100-1000
        dto.setQuantity(1 + random.nextInt(1000)); // 1-1000
        dto.setTimestamp(Instant.now());
        return dto;
    }
}