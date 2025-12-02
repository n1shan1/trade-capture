package com.pms.trade_capture.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "outbox_event")
@Data
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "trade_id", nullable = false)
    private UUID tradeId;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "bytea")
    private byte[] payload; // Protobuf serialized bytes

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    public OutboxEvent(UUID portfolioId, UUID tradeId, byte[] bytes) {
        this.id = UUID.randomUUID();
        this.portfolioId = portfolioId;
        this.tradeId = tradeId;
        this.payload = bytes;
    }

    protected OutboxEvent() {
    }
    //
    // public OutboxEvent(UUID portfolioId, UUID tradeId, byte[] payload) {
    // this.id = UUID.randomUUID();
    // this.portfolioId = portfolioId;
    // this.tradeId = tradeId;
    // this.payload = payload;
    // }
    //
    // // getters and setters...
}
