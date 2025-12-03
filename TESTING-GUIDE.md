# Trade Capture System - End-to-End Testing Guide

## Overview

This guide provides complete instructions for testing the trade capture system end-to-end, from trade generation through Kafka consumption.

## Architecture

```
┌─────────────┐     RabbitMQ        ┌──────────────┐     PostgreSQL      ┌─────────────┐
│  Simulator  │ ──► Streams ───────► │ trade-capture│ ──► (DB Tables) ──► │   Outbox    │
│             │                      │   Service    │                      │ Dispatcher  │
└─────────────┘                      └──────────────┘                      └─────────────┘
                                                                                   │
                                                                                   │ Kafka
                                                                                   ▼
                                                                            ┌─────────────┐
                                                                            │   Consumer  │
                                                                            └─────────────┘
```

### Component Flow:
1. **Simulator** → Generates trade events and publishes to RabbitMQ Streams
2. **trade-capture** → Consumes from RabbitMQ Streams, batches trades per portfolio
3. **Database** → Stores trades in `safe_store_trade` and creates `outbox_event` entries
4. **Outbox Dispatcher** → Polls pending outbox events and publishes to Kafka
5. **Consumer** → Receives trades from Kafka and logs them

---

## Prerequisites

### Required Services (Docker Compose)

All services must be running:

```bash
cd /mnt/c/Developer/trade-capture
docker-compose up -d
```

Verify all services are healthy:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

Expected output:
```
NAMES             STATUS
schema-registry   Up (healthy)
kafka             Up (healthy)
postgres          Up (healthy)
rabbitmq          Up (healthy)
```

### Service Ports:
- **PostgreSQL**: 5432
- **RabbitMQ Management**: 15672
- **RabbitMQ Streams**: 5552
- **Kafka**: 9092
- **Schema Registry**: 8081
- **trade-capture**: 8082
- **Kafka Consumer**: 4001

---

## Quick Start - 4 Terminal Setup

### Terminal 1: Kafka Consumer

**Purpose**: Receives and displays trades published to Kafka.

```bash
cd /mnt/c/Developer/trade-capture/kafka-consumer
KAFKA_TOPIC=raw-trades-proto mvn spring-boot:run
```

**Wait for this message**:
```
partitions assigned: [raw-trades-proto-0]
```

**Expected logs**:
```
Received trade: Trade[id=..., symbol=AAPL, side=BUY, quantity=100, price=150.25]
```

---

### Terminal 2: trade-capture Service

**Purpose**: Core service that consumes from RabbitMQ Streams, persists to DB, and manages outbox.

```bash
cd /mnt/c/Developer/trade-capture
mvn spring-boot:run
```

**Wait for this message**:
```
Started TradeCaptureApplication in X.XXX seconds
```

**Expected logs**:
```
INFO  --- [flush-scheduler] c.p.t.service.BatchingIngestService : Flushed 10 messages for portfolio <uuid>
INFO  --- [flush-scheduler] c.p.t.service.BatchingIngestService : Persisted batch: 10 trades, 10 outbox events
INFO  --- [tbox-dispatcher] c.p.t.outbox.OutboxDispatcher       : Published outbox event: id=<uuid>
```

---

### Terminal 3: Trade Simulator

**Purpose**: Generates realistic trade events and publishes to RabbitMQ Streams.

```bash
cd /mnt/c/Developer/trade-capture/simulator
mvn spring-boot:run
```

**Configuration** (in `simulator/src/main/resources/application.yaml`):
- Rate: 6 trades per minute (one every 10 seconds)
- Runs indefinitely until stopped
- Generates random: symbols (AAPL, GOOGL, MSFT, AMZN, TSLA, META, NVDA, NFLX), sides (BUY/SELL), quantities, prices

**Expected logs**:
```
INFO  --- [pool-6-thread-1] c.p.t.TradeSimulatorService : Sending trade event: portfolioId=..., tradeId=..., symbol=NVDA
INFO  --- [er-connection-0] c.p.t.TradeSimulatorService : Message sent successfully: tradeId=...
```

---

### Terminal 4: Real-time Monitoring

**Purpose**: Watch database state update in real-time.

```bash
watch -n 2 'docker exec -i postgres psql -U pms -d pmsdb -c "SELECT '\''Trades Captured'\'' as metric, COUNT(*)::text FROM safe_store_trade UNION ALL SELECT '\''Outbox (SENT)'\'', COUNT(*)::text FROM outbox_event WHERE status='\''SENT'\'' UNION ALL SELECT '\''Outbox (PENDING)'\'', COUNT(*)::text FROM outbox_event WHERE status='\''PENDING'\'';" 2>/dev/null'
```

**Expected output** (updates every 2 seconds):
```
      metric       | count
-------------------+-------
 Trades Captured   | 45
 Outbox (SENT)     | 45
 Outbox (PENDING)  | 0
```

---

## Verification Commands

### Check Database State

```bash
# Total trades captured
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT COUNT(*) as total_trades FROM safe_store_trade;"

# Outbox status breakdown
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"

# View recent trades
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT trade_id, symbol, side, quantity, price_per_stock, received_at FROM safe_store_trade ORDER BY received_at DESC LIMIT 10;"

# View sample outbox events
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT id, trade_id, status, attempts, created_at FROM outbox_event ORDER BY created_at DESC LIMIT 10;"
```

### Check Kafka Topics

```bash
# List all topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Describe the raw-trades topic
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic raw-trades-proto

# Check consumer group offsets
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group trade-consumer-group
```

### Check RabbitMQ Streams

```bash
# List streams
docker exec rabbitmq rabbitmq-streams list_streams

# Check stream info
docker exec rabbitmq rabbitmqctl list_queues name messages
```

### Check Schema Registry

```bash
# List registered schemas
curl -s http://localhost:8081/subjects

# Get specific schema details
curl -s http://localhost:8081/subjects/raw-trades-proto-value/versions/latest | jq
```

---

## Testing Scenarios

### Scenario 1: Basic Flow Verification

**Objective**: Verify trades flow end-to-end from simulator to consumer.

1. Start all 4 terminals as described above
2. Wait 30 seconds
3. Verify in Terminal 4 that counts are increasing
4. Check Terminal 1 (consumer) shows "Received trade" messages
5. Run verification commands to confirm DB state

**Success Criteria**:
- ✅ Trades captured = Outbox SENT
- ✅ Outbox PENDING = 0 (or very low)
- ✅ Consumer logs show received trades
- ✅ No errors in any terminal

---

### Scenario 2: Batching Behavior

**Objective**: Verify that trades are batched per portfolio before DB insert.

1. Start trade-capture service with increased logging:
   ```bash
   cd /mnt/c/Developer/trade-capture
   mvn spring-boot:run
   ```

2. Monitor logs for batch flush messages:
   ```bash
   tail -f target/trade-capture.log | grep "Flushed"
   ```

3. Start simulator
4. Observe batch sizes (should flush when buffer reaches 100 or after 200ms timeout)

**Success Criteria**:
- ✅ Batches are flushed per portfolio
- ✅ Batch sizes vary based on timing
- ✅ No individual inserts (all use `saveAll`)

---

### Scenario 3: Idempotency Test

**Objective**: Verify duplicate trades are handled correctly.

1. Clear database:
   ```bash
   docker exec -i postgres psql -U pms -d pmsdb -c "TRUNCATE TABLE safe_store_trade, outbox_event CASCADE;"
   ```

2. Start trade-capture and simulator
3. Let 10 trades accumulate
4. Restart trade-capture service (this will replay messages from RabbitMQ Streams)
5. Check for duplicate key violations (should be logged but handled):
   ```bash
   docker exec -i postgres psql -U pms -d pmsdb -c "SELECT COUNT(*) FROM safe_store_trade;"
   ```

**Success Criteria**:
- ✅ No duplicate trades in database
- ✅ Logs show "Duplicate trade_id detected (replay after crash), treating as idempotent"
- ✅ Trade count remains correct

---

### Scenario 4: Crash Recovery

**Objective**: Verify system recovers correctly after a crash.

1. Start all services
2. Let 20 trades accumulate
3. Kill trade-capture service (Ctrl+C)
4. Let simulator continue (trades queue in RabbitMQ)
5. Restart trade-capture
6. Verify all queued trades are processed

**Success Criteria**:
- ✅ All trades eventually processed
- ✅ No data loss
- ✅ Offsets committed correctly
- ✅ Outbox events all marked as SENT

---

### Scenario 5: Performance Test

**Objective**: Measure throughput and latency.

1. Modify simulator rate (edit `simulator/src/main/resources/application.yaml`):
   ```yaml
   simulator:
     rate: 600  # 600 trades per minute = 10/second
   ```

2. Clear database
3. Start all services
4. Run for 5 minutes
5. Measure:
   ```bash
   # Total throughput
   docker exec -i postgres psql -U pms -d pmsdb -c "SELECT COUNT(*) as total, MIN(received_at) as first, MAX(received_at) as last FROM safe_store_trade;"
   
   # Avg latency (received_at - event_timestamp)
   docker exec -i postgres psql -U pms -d pmsdb -c "SELECT AVG(EXTRACT(EPOCH FROM (received_at - event_timestamp))) as avg_latency_sec FROM safe_store_trade;"
   ```

**Success Criteria**:
- ✅ No backlog in RabbitMQ
- ✅ Latency < 1 second average
- ✅ All trades processed

---

## Troubleshooting

### Issue: Consumer Not Receiving Messages

**Symptoms**:
- Consumer shows "partitions assigned" but no "Received trade" logs
- Database shows outbox events with status='SENT' but consumer has no output

**Solutions**:
1. Check Schema Registry is healthy:
   ```bash
   curl http://localhost:8081/subjects
   ```

2. Verify topic exists:
   ```bash
   docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic raw-trades-proto
   ```

3. Check consumer group lag:
   ```bash
   docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group trade-consumer-group
   ```

4. Restart consumer with correct topic:
   ```bash
   KAFKA_TOPIC=raw-trades-proto mvn spring-boot:run
   ```

---

### Issue: Schema Registry Connection Refused

**Symptoms**:
```
java.net.ConnectException: Connection refused
Caused by Schema Registry connection error
```

**Solutions**:
1. Verify Schema Registry is running:
   ```bash
   docker ps | grep schema-registry
   ```

2. Check logs:
   ```bash
   docker-compose logs schema-registry | tail -50
   ```

3. Restart Schema Registry:
   ```bash
   docker-compose restart schema-registry
   ```

4. Verify Kafka is reachable from Schema Registry:
   ```bash
   docker exec schema-registry kafka-broker-api-versions --bootstrap-server kafka:19092
   ```

---

### Issue: "bytea vs bigint" Database Error

**Symptoms**:
```
ERROR: column "payload" is of type bytea but expression is of type bigint
```

**Root Cause**: Lombok's `@AllArgsConstructor` created constructor overload confusion.

**Solution**: Already fixed! Verify `OutboxEvent.java` has:
```java
@Entity
@Data
@NoArgsConstructor
// NO @AllArgsConstructor here!
public class OutboxEvent {
    // ... fields ...
    
    // Custom constructor only
    public OutboxEvent(UUID portfolioId, UUID tradeId, byte[] payload) {
        this.portfolioId = portfolioId;
        this.tradeId = tradeId;
        this.payload = payload;
    }
}
```

---

### Issue: Duplicate Key Violations

**Symptoms**:
```
ERROR: duplicate key value violates unique constraint "ux_safe_store_trade_tradeid"
```

**Expected Behavior**: This is normal during replay/recovery! The service handles it gracefully.

**Check logs for**:
```
DEBUG --- Duplicate trade_id detected (replay after crash), treating as idempotent
```

**If persistent**: Check if simulator is reusing trade IDs (should not happen with UUID.randomUUID()).

---

### Issue: RabbitMQ Stream Not Found

**Symptoms**:
```
Stream does not exist: trade-events-stream
```

**Solution**:
```bash
# Check if stream exists
docker exec rabbitmq rabbitmq-streams list_streams

# If missing, it will be auto-created on first publish
# Or manually create:
docker exec rabbitmq rabbitmq-streams add_stream trade-events-stream
```

---

## Performance Tuning

### Batch Size Configuration

Edit `src/main/resources/application.yaml`:

```yaml
app:
  ingest:
    batch:
      max-size-per-portfolio: 100   # Increase for higher throughput
      flush-interval-ms: 200        # Decrease for lower latency
```

### JDBC Batching

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50            # Increase for better bulk insert performance
```

### Outbox Dispatcher

```yaml
app:
  outbox:
    poll-interval-ms: 500           # Decrease for lower outbox latency
    batch-size: 100                 # Increase for higher Kafka throughput
```

---

## Cleanup

### Stop All Services

```bash
# Stop Java applications (Ctrl+C in each terminal)

# Stop Docker services
docker-compose down

# Keep data volumes
docker-compose down

# Remove data volumes (CAUTION: deletes all data!)
docker-compose down -v
```

### Clear Test Data Only

```bash
docker exec -i postgres psql -U pms -d pmsdb -c "TRUNCATE TABLE safe_store_trade, outbox_event CASCADE;"
```

---

## Configuration Reference

### Simulator Configuration
File: `simulator/src/main/resources/application.yaml`

```yaml
simulator:
  rate: 6                    # Trades per minute
  run-indefinitely: true     # Run continuously
```

### trade-capture Configuration
File: `src/main/resources/application.yaml`

```yaml
app:
  rabbit:
    stream:
      name: trade-events-stream
  ingest:
    batch:
      max-size-per-portfolio: 100
      flush-interval-ms: 200
  outbox:
    poll-interval-ms: 500
    batch-size: 100
```

### Consumer Configuration
File: `kafka-consumer/src/main/resources/application.yaml`

```yaml
app:
  kafka:
    topic: raw-trades-proto   # Must match trade-capture's TRADE_TOPIC_NAME
```

---

## Key Fixes Applied

### 1. ✅ Payload Binding Issue Fixed
- **Problem**: `byte[] payload` was being bound as `bigint` in SQL
- **Root Cause**: Lombok `@AllArgsConstructor` constructor overload confusion
- **Solution**: Removed `@AllArgsConstructor`, kept only custom 3-arg constructor

### 2. ✅ Schema Registry Network Issue Fixed
- **Problem**: Schema Registry couldn't connect to Kafka (localhost vs container hostname)
- **Solution**: 
  - Added internal listener `PLAINTEXT_INTERNAL://kafka:19092`
  - Schema Registry uses internal listener
  - Host applications use external listener `localhost:9092`

### 3. ✅ Stream Name Mismatch Fixed
- **Problem**: Simulator published to wrong stream name
- **Solution**: Standardized on `trade-events-stream`

### 4. ✅ Missing Timestamp Field Fixed
- **Problem**: `SafeStoreTrade` missing `timestamp` column
- **Solution**: Added `timestamp` field to entity

---

## Success Metrics

After running for 5 minutes, you should see:

- **~30 trades captured** (at default rate of 6/min)
- **0 errors** in any logs (Schema Registry connection warnings during startup are OK)
- **Outbox PENDING = 0** (or very low, < 5)
- **All trades visible in consumer logs**
- **DB query latency < 50ms** (check with `EXPLAIN ANALYZE`)

---

## Support & Documentation

- **Architecture**: See `refactor.prompt.md`
- **Test Script**: Run `./test-e2e-flow.sh` for automated test
- **Snapshot**: Run `./create-snapshot.sh` to backup code
- **Issues**: Check logs in `target/trade-capture.log`

---

**Last Updated**: December 3, 2025
**System Version**: v1.0.0 (RabbitMQ Streams + Outbox Pattern)
