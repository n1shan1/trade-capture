package com.pms.trade_capture.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "safe_store_trade")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SafeStoreTrade {

    @Id
    private UUID id;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "trade_id", nullable = false, unique = true)
    private UUID tradeId;

    private String symbol;
    private String side;

    @Column(name = "price_per_stock")
    private double pricePerStock;

    private long quantity;

    private LocalDateTime timestamp;

    public SafeStoreTrade(UUID portfolioId, UUID tradeId, String symbol, String side, double pricePerStock, long quantity, LocalDateTime localDateTime) {
    }

//    protected SafeStoreTrade() {}

//    public SafeStoreTrade(UUID portfolioId, UUID tradeId, String symbol, String side,
//                          double pricePerStock, long quantity, LocalDateTime timestamp) {
//        this.id = UUID.randomUUID();
//        this.portfolioId = portfolioId;
//        this.tradeId = tradeId;
//        this.symbol = symbol;
//        this.side = side;
//        this.pricePerStock = pricePerStock;
//        this.quantity = quantity;
//        this.timestamp = timestamp;
//    }
//
//    // getters...
}

