package com.pms.trade_capture.debug;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class JdbcByteaInsertTest {

    @Test
    public void insertAndReadBytea_shouldSucceed() throws Exception {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String db = System.getenv().getOrDefault("DB_NAME", "pmsdb");
        String user = System.getenv().getOrDefault("DB_USER", "pms");
        String pass = System.getenv().getOrDefault("DB_PASS", "pms");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);

            UUID portfolioId = UUID.randomUUID();
            UUID tradeId = UUID.randomUUID();
            byte[] payload = new byte[]{10, 2, 3, 4, 5};

            String insert = "INSERT INTO outbox_event (portfolio_id, trade_id, payload) VALUES (?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setObject(1, portfolioId);
                ps.setObject(2, tradeId);
                ps.setBytes(3, payload);
                int rows = ps.executeUpdate();
                assertEquals(1, rows, "insert should affect 1 row");
            }

            // Read it back
            String select = "SELECT payload, octet_length(payload) FROM outbox_event WHERE trade_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setObject(1, tradeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Inserted row must be selectable");
                    byte[] read = rs.getBytes(1);
                    int len = rs.getInt(2);
                    assertNotNull(read);
                    assertEquals(payload.length, len, "length should match");
                    assertArrayEquals(payload, read, "payload bytes should match");
                }
            }

            // cleanup
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM outbox_event WHERE trade_id = ?")) {
                ps.setObject(1, tradeId);
                ps.executeUpdate();
            }

            conn.commit();
        }
    }
}
