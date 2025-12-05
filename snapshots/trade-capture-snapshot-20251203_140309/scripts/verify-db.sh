#!/usr/bin/env bash
psql -U pms -h localhost -d pmsdb -c "SELECT count(*) FROM safe_store_trade;"
psql -U pms -h localhost -d pmsdb -c "SELECT count(*) FROM outbox_event WHERE status = 'PENDING';"
psql -U pms -h localhost -d pmsdb -c "SELECT count(*) FROM outbox_event WHERE status = 'SENT';"
