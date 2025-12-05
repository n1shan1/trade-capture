You are helping me REFACTOR the existing `pms-trade-capture` service so that it correctly uses
**RabbitMQ Streams**, **Protobuf**, **DB batching**, and **offset-based reliability**.

IMPORTANT: This refactor must REMOVE all classic RabbitMQ queue semantics such as:

- deliveryTag
- Channel
- basicAck()
- multiple acknowledge flags

Those only work with classic AMQP queues.  
The new architecture ONLY uses **RabbitMQ Streams** and relies on the Streams offset/commit model.

=====================================================================

1. # CURRENT PROBLEM

The previous version of the project used classic RabbitMQ queues with:

- manual ACK (basicAck)
- deliveryTag tracking
- RAM buffer holding TradeEventProto until DB commit
- “only ACK after DB commit” crash-safety pattern

Now the system is migrated to **RabbitMQ Streams**, but the old AMQP logic is still in the code.

I want to REMOVE all queue-based logic and REPLACE it with proper
RabbitMQ Stream offset management.

===================================================================== 2. TARGET ARCHITECTURE & BEHAVIOR
=====================================================================

### 2.1 RabbitMQ Streams (NOT AMQP queues)

Use the Streams client created via `Environment.consumerBuilder()`.
Consume messages with a **named consumer** or **consumer group**.

For each message:

- Parse payload as Protobuf `TradeEventProto`
- Get the **stream offset** from the message context (NOT deliveryTag)

### 2.2 In-Memory Batching (critical)

Use a batching layer such as:

    Map<String /* portfolioId */, List<PendingStreamMessage>>

Where:

    class PendingStreamMessage {
        TradeEventProto trade;
        long offset;  // RabbitMQ Stream offset, NOT deliveryTag
    }

### 2.3 Crash Safety (Streams Version)

The safety rule must now be:

**"Do not commit the stream offset until AFTER the DB commit succeeds."**

Equivalent to AMQP “ack only after DB commit”, but using Stream offsets.

If the Pod crashes:

- RAM buffer is lost
- Offsets were NOT stored → uncommitted stream offsets
- New consumer resumes from the last committed offset
- All messages after that offset are replayed

This ensures **no data loss**, identical reliability to AMQP manual-ack.

### 2.4 DB Write + Offset Commit Flow

When a portfolio buffer is flushed:

1. Extract TradeEventProto list
2. Inside a @Transactional method:
   - saveAll into safe_store_trade
   - saveAll into outbox_event
3. After DB commit succeeds:
   - COMMIT the RabbitMQ Stream offset (`consumer.storeOffset(offset)` OR similar method)
4. Clear the in-RAM buffer for that portfolio

If DB fails:

- DO NOT commit the offset
- The service may crash → stream will replay from last committed offset

### 2.5 Idempotency (must preserve)

Because a crash may occur AFTER DB commit but BEFORE offset commit,
DB must safely reject duplicates using:

- UNIQUE constraint on `trade_id`
- Or UPSERT (`ON CONFLICT DO NOTHING`)
- On duplicate, treat as already processed, then commit the offset

===================================================================== 3. WHAT TO REFACTOR NOW
=====================================================================

### A. Remove AMQP concepts

Remove:

- deliveryTag
- Channel
- basicAck
- acknowledgment flags
- any AMQP queue listener like @RabbitListener

### B. Introduce RabbitMQ Stream offset handling

Implement:

- `PendingStreamMessage { TradeEventProto, long offset }`
- Use offset from message metadata provided by RabbitMQ Streams client
- Batch offsets by portfolioId exactly like trades

### C. Implement offset commit after flushing

After successful DB batch:

    long highestOffset = batch.get(batch.size()-1).offset;
    consumer.storeOffset(highestOffset);

Avoid committing offsets individually — commit only for batch boundaries.

### D. Use consumer group or named consumer

Refactor RabbitStreamConfig to support:

- `@Value` injection for:

  - stream name
  - stream host
  - stream port
  - consumer name
  - consumer-group name (if enabled)

- Create the consumer using builder:

      consumer = environment.consumerBuilder()
                 .stream(streamName)
                 .group(consumerGroupName)   // for sharding support
                 .offset(OffsetSpecification.next())
                 .messageHandler((ctx, msg) -> handleMessage(msg, msgOffset))
                 .build();

### E. Refactor the existing BatchingIngestService

Rewrite:

- to store `PendingStreamMessage`
- to flush based on:

  - max batch size per portfolio
  - max interval

- to commit offsets AFTER DB

### F. Maintain DB idempotency

Ensure:

- UNIQUE(trade_id)
- swallowing DuplicateKeyException as safe replay duplicate
- still commit the offset to avoid infinite loops

===================================================================== 4. CODE TO BE GENERATED OR UPDATED
=====================================================================
Copilot, please generate/refactor the following components:

1. A correct RabbitStreamConfig using `Environment.builder()`, no hardcoded values.
2. A stream-based consumer class:
   - Reads Protobuf bytes
   - Extracts stream offset
   - Passes `(TradeEventProto, offset)` to batching service
3. The new `PendingStreamMessage` class
4. Updated `BatchingIngestService`:
   - Map<String, List<PendingStreamMessage>>
   - Flush logic
   - DB write + offset commit
5. Update OutboxDispatcher (unchanged except idempotency)
6. Update application.yml to support:
   - rabbit stream configs
   - batching configs
7. Ensure ALL old AMQP references are removed.

===================================================================== 5. QUALITY REQUIREMENTS
=====================================================================

- Java 21
- Spring Boot 3.x
- Production-grade structure (config / service / stream / domain / repository)
- No hardcoded magic strings
- Thread-safe batching
- Clear comments explaining offset-based reliability
- Clean, understandable, maintainable code

===================================================================== 6. OUTPUT
=====================================================================
Generate the refactored code and configuration for this new architecture.
