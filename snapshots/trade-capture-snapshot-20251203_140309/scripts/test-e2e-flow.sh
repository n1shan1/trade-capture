#!/bin/bash

# E2E Flow Test Script
# This script demonstrates the complete trade capture flow:
# Simulator → RabbitMQ Streams → trade-capture → PostgreSQL → Kafka → Consumer

set -e

echo "=========================================="
echo "End-to-End Trade Capture Flow Test"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check prerequisites
echo -e "${BLUE}1. Checking prerequisites...${NC}"
docker ps | grep -q postgres && echo "  ✓ PostgreSQL running" || { echo "  ✗ PostgreSQL not running"; exit 1; }
docker ps | grep -q rabbitmq && echo "  ✓ RabbitMQ running" || { echo "  ✗ RabbitMQ not running"; exit 1; }
docker ps | grep -q kafka && echo "  ✓ Kafka running" || { echo "  ✗ Kafka not running"; exit 1; }
docker ps | grep -q schema-registry && echo "  ✓ Schema Registry running" || { echo "  ✗ Schema Registry not running"; exit 1; }
echo ""

# Clear previous data
echo -e "${BLUE}2. Clearing previous test data...${NC}"
docker exec -i postgres psql -U pms -d pmsdb -c "TRUNCATE TABLE safe_store_trade, outbox_event CASCADE;" > /dev/null 2>&1
echo "  ✓ Database tables cleared"
echo ""

# Check if trade-capture is running
echo -e "${BLUE}3. Checking trade-capture service...${NC}"
if pgrep -f 'trade-capture.*jar' > /dev/null; then
    echo "  ✓ trade-capture is running"
else
    echo "  ✗ trade-capture is NOT running"
    echo "  Starting trade-capture..."
    cd /mnt/c/Developer/trade-capture
    java -jar target/trade-capture-0.0.1-SNAPSHOT.jar > target/trade-capture.log 2>&1 &
    sleep 8
    echo "  ✓ trade-capture started"
fi
echo ""

# Show initial counts
echo -e "${BLUE}4. Initial database state:${NC}"
docker exec -i postgres psql -U pms -d pmsdb -c "SELECT 'safe_store_trade' as table_name, COUNT(*) as count FROM safe_store_trade UNION ALL SELECT 'outbox_event', COUNT(*) FROM outbox_event;" 2>/dev/null
echo ""

echo -e "${YELLOW}=========================================="
echo "Starting Test Components"
echo "==========================================${NC}"
echo ""

echo -e "${GREEN}Terminal 1: Kafka Consumer (receiving from Kafka)${NC}"
echo "  Command: cd kafka-consumer && java -jar target/*.jar"
echo "  This will display trades received from Kafka"
echo ""

echo -e "${GREEN}Terminal 2: Trade Simulator (publishing to RabbitMQ Streams)${NC}"
echo "  Command: cd simulator && java -jar target/*.jar"
echo "  This will generate and publish 50 trades"
echo ""

echo -e "${GREEN}Terminal 3: Monitor trade-capture logs${NC}"
echo "  Command: tail -f target/trade-capture.log | grep -E 'Persisted batch|Flushed|messages for portfolio'"
echo "  This shows the batch processing"
echo ""

echo -e "${YELLOW}=========================================="
echo "Test Execution Plan"
echo "==========================================${NC}"
echo ""
echo "1. Start the Kafka consumer (Terminal 1)"
echo "2. Start the trade simulator (Terminal 2)"
echo "3. Monitor the logs (Terminal 3)"
echo "4. After ~10 seconds, check final counts with:"
echo "   docker exec -i postgres psql -U pms -d pmsdb -c \"SELECT COUNT(*) FROM safe_store_trade; SELECT COUNT(*) FROM outbox_event;\""
echo ""

echo -e "${BLUE}Ready to start! Press Enter to launch terminals...${NC}"
read -r

# Launch terminals
echo "Launching components..."

# Note: Adjust these commands based on your terminal emulator
# For WSL with Windows Terminal or similar:
if command -v wt.exe &> /dev/null; then
    # Windows Terminal
    wt.exe -w 0 nt -d /mnt/c/Developer/trade-capture/kafka-consumer bash -c "echo 'KAFKA CONSUMER - Waiting for trades from Kafka...'; echo ''; java -jar target/*.jar; exec bash"
    sleep 1
    wt.exe -w 0 nt -d /mnt/c/Developer/trade-capture/simulator bash -c "echo 'TRADE SIMULATOR - Publishing 50 trades to RabbitMQ Streams...'; echo ''; java -jar target/*.jar; exec bash"
    sleep 1
    wt.exe -w 0 nt -d /mnt/c/Developer/trade-capture bash -c "echo 'TRADE-CAPTURE LOGS - Batch processing...'; echo ''; tail -f target/trade-capture.log | grep --line-buffered -E 'Persisted batch|Flushed|messages for portfolio|Outbox payload'; exec bash"
    echo "  ✓ Launched in Windows Terminal tabs"
else
    # Fallback: just show the commands
    echo ""
    echo "Please open 3 separate terminals and run:"
    echo ""
    echo -e "${GREEN}Terminal 1 (Consumer):${NC}"
    echo "  cd /mnt/c/Developer/trade-capture/kafka-consumer && java -jar target/*.jar"
    echo ""
    echo -e "${GREEN}Terminal 2 (Simulator):${NC}"
    echo "  cd /mnt/c/Developer/trade-capture/simulator && java -jar target/*.jar"
    echo ""
    echo -e "${GREEN}Terminal 3 (Monitor):${NC}"
    echo "  cd /mnt/c/Developer/trade-capture && tail -f target/trade-capture.log | grep -E 'Persisted batch|Flushed'"
fi

echo ""
echo "Waiting 15 seconds for processing..."
sleep 15

echo ""
echo -e "${BLUE}Final Results:${NC}"
docker exec -i postgres psql -U pms -d pmsdb -c "
SELECT 
    'Total Trades Captured' as metric, 
    COUNT(*)::text as value 
FROM safe_store_trade
UNION ALL
SELECT 
    'Outbox Events Created', 
    COUNT(*)::text 
FROM outbox_event
UNION ALL
SELECT 
    'Outbox Events Sent to Kafka', 
    COUNT(*)::text 
FROM outbox_event 
WHERE status = 'SENT';
" 2>/dev/null

echo ""
echo -e "${GREEN}Test complete!${NC}"
