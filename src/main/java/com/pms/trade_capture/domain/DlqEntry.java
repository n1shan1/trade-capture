package com.pms.trade_capture.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dlq_entry")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlqEntry {

    @Id
    private UUID id;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt = Instant.now();

    @Lob
    @Column(name = "raw_message", nullable = false, columnDefinition = "text")
    private String rawMessage;

    @Column(name = "error_detail")
    private String errorDetail;

    public DlqEntry(String s, String message) {
    }

    // protected DlqEntry() {}
    //
    // public DlqEntry(String rawMessage, String errorDetail) {
    // this.id = UUID.randomUUID();
    // this.rawMessage = rawMessage;
    // this.errorDetail = errorDetail;
    // }
    //
    // // getters...
}
