# End-to-End Trade Capture Flow Test

## Architecture Flow
```
Simulator → RabbitMQ Streams → trade-capture → PostgreSQL → Outbox Dispatcher → Kafka → Consumer
```

## Prerequisites Check
All services are running:
- ✅ PostgreSQL (port 5432)
- ✅ RabbitMQ Streams (port 5552)
- ✅ Kafka (port 9092)
- ✅ Schema Registry (port 8081)
- ✅ trade-capture application (port 8082)

---

## Test Instructions

### Terminal 1: Start Kafka Consumer
This terminal will display trades received from Kafka (published by the outbox dispatcher).

```bash
cd /mnt/c/Developer/trade-capture/kafka-consumer
java -jar target/kafka-consumer-0.0.1-SNAPSHOT.jar
```

**Expected Output:**
- Application starts and connects to Kafka
- Waits for messages on `raw-trades-proto` topic
- Will display: `"Received trade: Trade[id=..., symbol=..., side=..., ...]"`

---

### Terminal 2: Start Trade Simulator
This terminal will generate and publish trades to RabbitMQ Streams.

```bash
cd /mnt/c/Developer/trade-capture/simulator
java -jar target/trade-simulator-0.0.1-SNAPSHOT.jar
```

**Expected Output:**
- Publishes 50 trades to RabbitMQ Streams
- Shows: `"Published 50 trades to stream trade-events-stream"`
- Application exits when done

---

### Terminal 3: Monitor trade-capture Service
This terminal shows the batch processing in real-time.

```bash
cd /mnt/c/Developer/trade-capture
tail -f target/trade-capture.log | grep --line-buffered -E 'Persisted batch|Flushed|payload debug'
```

**Expected Output:**
- Shows batch flushes: `"Flushed N messages for portfolio <uuid>"`
- Shows DB persistence: `"Persisted batch: N trades, N outbox events"`
- Shows payload validation: `"Outbox payload debug: tradeId=..., length=112, class=[B"`

---

### Terminal 4: Monitor Database Counts (Optional)
Watch the database grow in real-time.

```bash
watch -n 1 'docker exec -i postgres psql -U pms -d pmsdb -c "SELECT '\''safe_store_trade'\'' as table_name, COUNT(*) as count FROM safe_store_trade UNION ALL SELECT '\''outbox_event'\'', COUNT(*) FROM outbox_event WHERE status='\''PENDING'\'' UNION ALL SELECT '\''outbox_sent'\'', COUNT(*) FROM outbox_event WHERE status='\''SENT'\'';" 2>/dev/null'
```

**Expected Output:**
- `safe_store_trade`: Count increases to 50
- `outbox_event` (PENDING): Decreases as events are published
- `outbox_sent` (SENT): Increases to 50

---

## Verification Commands

### After running the test, verify the complete flow:

```bash
# Check total trades captured
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT COUNT(*) as total_trades FROM safe_store_trade;"

# Check outbox events sent to Kafka
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"

# View sample trades
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT trade_id, symbol, side, quantity, price_per_stock, received_at FROM safe_store_trade ORDER BY received_at DESC LIMIT 5;"

# Check outbox dispatcher activity
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT id, trade_id, status, attempts, created_at FROM outbox_event ORDER BY created_at DESC LIMIT 5;"
```

---

## Expected Results

After running the simulator:
1. **50 trades** published to RabbitMQ Streams
2. **50 trades** captured in `safe_store_trade` table
3. **50 outbox events** created in `outbox_event` table
4. **50 outbox events** marked as `SENT` after Kafka publish
5. **50 trades** received by the Kafka consumer
6. **No `bytea vs bigint` errors** ✅
7. **No duplicate trade_id violations** (after first insert)

---

## Cleanup

```bash
# Stop simulator and consumer (Ctrl+C in their terminals)

# Clear test data
docker exec -i postgres psql -U pms -d pmsdb -c "TRUNCATE TABLE safe_store_trade, outbox_event CASCADE;"

# Stop trade-capture if needed
pkill -f 'trade-capture.*jar'
```

---

## Troubleshooting

### If consumer doesn't receive messages:
- Check Schema Registry is healthy: `docker ps | grep schema-registry`
- Check Kafka topic exists: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list`

### If simulator can't publish:
- Check RabbitMQ Stream: `docker exec rabbitmq rabbitmq-streams list_streams`

### If trade-capture has errors:
- Check logs: `tail -100 target/trade-capture.log`
- Look for DB connection issues or mapping errors

---

## Architecture Verification Points

✅ **Step 1:** Simulator → RabbitMQ  
   _Verify: Simulator logs show "Published N trades"_

✅ **Step 2:** RabbitMQ → trade-capture  
   _Verify: trade-capture logs show "Received message" or batch activity_

✅ **Step 3:** trade-capture → PostgreSQL  
   _Verify: `SELECT COUNT(*) FROM safe_store_trade` increases_

✅ **Step 4:** PostgreSQL Outbox → Kafka  
   _Verify: `SELECT COUNT(*) FROM outbox_event WHERE status='SENT'` increases_

✅ **Step 5:** Kafka → Consumer  
   _Verify: Consumer terminal shows "Received trade: ..."_

