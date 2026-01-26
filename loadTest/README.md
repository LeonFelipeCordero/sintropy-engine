# K6 Load Test for Sintropy Engine

Load testing suite using [k6](https://k6.io/) to validate the message broker under various channel types, routing configurations, and consumption patterns.

## Prerequisites

Install k6: https://k6.io/docs/get-started/installation/

## Quick Start

```bash
# Start the application
./gradlew quarkusDev -Dapi.version=1.44

# Run load test with default settings
k6 run loadTest/main.js
```

## Usage

### Default Configuration

```bash
k6 run loadTest/main.js
```

Default settings:
- Start RPS: 1
- Max RPS: 100
- Ramp-up: 2 minutes
- Steady state: 5 minutes
- Ramp-down: 1 minute

### Custom Load Profiles

```bash
# Higher load
k6 run -e START_RPS=10 -e MAX_RPS=500 -e STEADY=10m loadTest/main.js

# Quick smoke test
k6 run -e MAX_RPS=10 -e RAMP_UP=30s -e STEADY=1m loadTest/main.js

# Extended duration
k6 run -e MAX_RPS=200 -e RAMP_UP=5m -e STEADY=30m -e RAMP_DOWN=5m loadTest/main.js
```

### Custom URLs

```bash
k6 run -e BASE_URL=http://myhost:8080 -e WS_URL=ws://myhost:8080 loadTest/main.js
```

### Polling Configuration

```bash
# Larger batch size, faster polling
k6 run -e POLL_BATCH=50 -e POLL_INTERVAL=50 loadTest/main.js
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BASE_URL` | `http://localhost:8080` | REST API base URL |
| `WS_URL` | `ws://localhost:8080` | WebSocket base URL |
| `START_RPS` | `1` | Initial requests per second |
| `MAX_RPS` | `100` | Maximum requests per second |
| `RAMP_UP` | `2m` | Ramp-up duration |
| `STEADY` | `5m` | Steady state duration |
| `RAMP_DOWN` | `1m` | Ramp-down duration |
| `POLL_BATCH` | `10` | Messages per poll request |
| `POLL_INTERVAL` | `100` | Milliseconds between polls |

## Test Scenarios

The load test runs these scenarios concurrently:

### Writers
- Publishes messages to 9 write-target channels
- Uses ramping arrival rate (configurable RPS)
- Mixed payload sizes: 50% small (~100B), 35% medium (~1KB), 15% large (~10KB)

### Pollers
- 20 VUs continuously polling all 10 queue channels
- Bulk acknowledgment of messages

### WebSocket Consumers
- 1 connection to `stream-ws-1`
- 3 connections to `stream-ws-3`
- 6 connections to `stream-ws-6`

## Channel Architecture

```
15 Channels Total:
├── 5 STREAM channels (2 write-only, 3 with WebSocket consumers)
├── 5 QUEUE STANDARD channels
└── 5 QUEUE FIFO channels

10 Routes:
├── stream-ingest-1 → queue-standard-1, queue-fifo-1
├── stream-ingest-2 → queue-standard-2, queue-fifo-2
├── stream-ws-1 → queue-standard-5
├── stream-ws-3 → queue-fifo-5
├── stream-ws-6 → queue-standard-5, queue-fifo-5
├── queue-standard-3 → queue-standard-4
└── queue-fifo-3 → queue-fifo-4
```

## Metrics

### Custom Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `publish_duration` | Trend | Time to publish a message |
| `publish_success` | Counter | Successfully published messages |
| `publish_failure` | Counter | Failed publish attempts |
| `poll_duration` | Trend | Time to poll messages |
| `messages_polled` | Counter | Total messages polled |
| `messages_acked` | Counter | Total messages acknowledged |
| `poll_errors` | Counter | Poll request errors |
| `ws_messages_received` | Counter | Messages received via WebSocket |
| `ws_connection_errors` | Counter | WebSocket connection failures |

### Thresholds

| Metric | Threshold |
|--------|-----------|
| `publish_duration` | p95 < 500ms, p99 < 1000ms |
| `poll_duration` | p95 < 500ms, p99 < 1000ms |
| `http_req_duration` | p95 < 1000ms |
| `http_req_failed` | rate < 1% |
| `ws_connection_errors` | count < 10 |

## Output Options

```bash
# JSON output
k6 run --out json=results.json loadTest/main.js

# CSV output
k6 run --out csv=results.csv loadTest/main.js

# InfluxDB (for Grafana dashboards)
k6 run --out influxdb=http://localhost:8086/k6 loadTest/main.js
```

## File Structure

```
loadTest/
├── README.md              # This file
├── plan.md                # Test plan documentation
├── config.js              # All configurable parameters
├── setup.js               # Channel, producer, route creation
├── teardown.js            # Cleanup after tests
├── main.js                # Main test orchestration
├── lib/
│   ├── api.js             # API helper functions
│   └── generators.js      # Message payload generators
└── scenarios/
    ├── writers.js         # Message publishing scenario
    ├── pollers.js         # Queue polling scenario
    └── websockets.js      # WebSocket consumer scenario
```

## Troubleshooting

### Connection Refused
Ensure the application is running on the configured URL:
```bash
curl http://localhost:8080/channels
```

### WebSocket Failures
Check that PostgreSQL logical replication is configured:
```bash
docker-compose -f development/docker-compose.yaml up -d
```

### High Error Rate
- Reduce `MAX_RPS` to find sustainable throughput
- Increase `POLL_INTERVAL` to reduce database load
- Check application logs for errors
