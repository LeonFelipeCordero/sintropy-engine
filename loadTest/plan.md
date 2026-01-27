# K6 Load Test Plan for Sintropy Engine

## Overview

Load testing setup using k6 to validate the message broker under load with various channel types, routing configurations, and consumption patterns.

## Channel Architecture

### Channel Summary (15 total)
| Type | Count | Purpose |
|------|-------|---------|
| STREAM | 5 | 2 write-only (routing), 3 with WebSocket consumers |
| QUEUE STANDARD | 5 | Direct writes + routed messages, polled |
| QUEUE FIFO | 5 | Direct writes + routed messages, polled |

### Detailed Channel Configuration

#### STREAM Channels (5)
| Name | WebSocket Consumers | Routes To | Receives Direct Writes |
|------|---------------------|-----------|------------------------|
| `stream-ingest-1` | 0 | queue-standard-1, queue-fifo-1 | Yes |
| `stream-ingest-2` | 0 | queue-standard-2, queue-fifo-2 | Yes |
| `stream-ws-1` | 1 | queue-standard-5 | Yes |
| `stream-ws-3` | 3 | queue-fifo-5 | Yes |
| `stream-ws-6` | 6 | queue-standard-5, queue-fifo-5 | Yes |

#### QUEUE STANDARD Channels (5)
| Name | Receives From | Routes To | Direct Writes |
|------|---------------|-----------|---------------|
| `queue-standard-1` | stream-ingest-1 | - | No |
| `queue-standard-2` | stream-ingest-2 | - | No |
| `queue-standard-3` | - | queue-standard-4 | Yes |
| `queue-standard-4` | queue-standard-3 | - | No |
| `queue-standard-5` | stream-ws-1, stream-ws-6 | - | Yes |

#### QUEUE FIFO Channels (5)
| Name | Receives From | Routes To | Direct Writes |
|------|---------------|-----------|---------------|
| `queue-fifo-1` | stream-ingest-1 | - | No |
| `queue-fifo-2` | stream-ingest-2 | - | No |
| `queue-fifo-3` | - | queue-fifo-4 | Yes |
| `queue-fifo-4` | queue-fifo-3 | - | No |
| `queue-fifo-5` | stream-ws-3, stream-ws-6 | - | Yes |

## Message Flow Diagram

```
                    ┌─────────────────┐
                    │ stream-ingest-1 │ (write-only)
                    └────────┬────────┘
                             │ routes to
              ┌──────────────┴──────────────┐
              ▼                             ▼
    ┌─────────────────┐           ┌─────────────────┐
    │ queue-standard-1│           │  queue-fifo-1   │
    └─────────────────┘           └─────────────────┘
           ▲ poll                        ▲ poll

                    ┌─────────────────┐
                    │ stream-ingest-2 │ (write-only)
                    └────────┬────────┘
                             │ routes to
              ┌──────────────┴──────────────┐
              ▼                             ▼
    ┌─────────────────┐           ┌─────────────────┐
    │ queue-standard-2│           │  queue-fifo-2   │
    └─────────────────┘           └─────────────────┘
           ▲ poll                        ▲ poll

    ┌─────────────────┐           ┌─────────────────┐
    │ queue-standard-3│──routes──▶│ queue-standard-4│
    └─────────────────┘           └─────────────────┘
           ▲ write                       ▲ poll

    ┌─────────────────┐           ┌─────────────────┐
    │  queue-fifo-3   │──routes──▶│  queue-fifo-4   │
    └─────────────────┘           └─────────────────┘
           ▲ write                       ▲ poll

    ┌─────────────────┐                               ┌─────────────────┐
    │ queue-standard-5│◀──────────────────────────────│  queue-fifo-5   │
    └─────────────────┘                               └─────────────────┘
           ▲ write + poll + routed                         ▲ write + poll + routed
           │                                               │
           │  ┌────────────────────────────────────────────┤
           │  │                                            │
    ┌──────┴──┴───────┐     ┌─────────────────┐     ┌─────┴───────────┐
    │  stream-ws-1    │     │  stream-ws-3    │     │  stream-ws-6    │
    │  (1 WS client)  │     │  (3 WS clients) │     │  (6 WS clients) │
    │ routes→std-5    │     │ routes→fifo-5   │     │ routes→std-5,   │
    └─────────────────┘     └─────────────────┘     │         fifo-5  │
           ▲ write                 ▲ write          └─────────────────┘
                                                           ▲ write
```

## Message Payload Configuration

Mixed payload sizes to test different scenarios:

| Size | Weight | Approx Size | Description |
|------|--------|-------------|-------------|
| Small | 50% | ~100 bytes | Simple JSON: `{id, timestamp, type}` |
| Medium | 35% | ~1KB | Nested JSON with arrays |
| Large | 15% | ~10KB | Complex payload with embedded data |

```javascript
// Payload distribution (configurable)
payloadDistribution: {
  small: 0.50,   // 50% small messages
  medium: 0.35,  // 35% medium messages
  large: 0.15,   // 15% large messages
}
```

## Load Profile Configuration

```javascript
// config.js - All configurable parameters
export const config = {
  // API settings
  baseUrl: __ENV.BASE_URL || 'http://localhost:8080',
  wsUrl: __ENV.WS_URL || 'ws://localhost:8080',

  // Load settings (configurable via env vars)
  load: {
    startRps: parseInt(__ENV.START_RPS) || 1,
    maxRps: parseInt(__ENV.MAX_RPS) || 100,
    rampUpDuration: __ENV.RAMP_UP || '2m',
    steadyDuration: __ENV.STEADY || '5m',
    rampDownDuration: __ENV.RAMP_DOWN || '1m',
  },

  // Polling settings
  polling: {
    batchSize: parseInt(__ENV.POLL_BATCH) || 10,
    intervalMs: parseInt(__ENV.POLL_INTERVAL) || 100,
  },

  // Payload distribution
  payloads: {
    small: { weight: 0.50, size: 100 },
    medium: { weight: 0.35, size: 1024 },
    large: { weight: 0.15, size: 10240 },
  },

  // Channel definitions (fully configurable)
  channels: {
    streams: [
      { name: 'stream-ingest-1', routingKey: 'events', wsConsumers: 0 },
      { name: 'stream-ingest-2', routingKey: 'events', wsConsumers: 0 },
      { name: 'stream-ws-1', routingKey: 'events', wsConsumers: 1 },
      { name: 'stream-ws-3', routingKey: 'events', wsConsumers: 3 },
      { name: 'stream-ws-6', routingKey: 'events', wsConsumers: 6 },
    ],
    standardQueues: [
      { name: 'queue-standard-1', routingKey: 'events' },
      { name: 'queue-standard-2', routingKey: 'events' },
      { name: 'queue-standard-3', routingKey: 'events' },
      { name: 'queue-standard-4', routingKey: 'events' },
      { name: 'queue-standard-5', routingKey: 'events' },
    ],
    fifoQueues: [
      { name: 'queue-fifo-1', routingKey: 'events' },
      { name: 'queue-fifo-2', routingKey: 'events' },
      { name: 'queue-fifo-3', routingKey: 'events' },
      { name: 'queue-fifo-4', routingKey: 'events' },
      { name: 'queue-fifo-5', routingKey: 'events' },
    ],
  },

  // Routes (channel links)
  routes: [
    // Stream to Queue routes
    { source: 'stream-ingest-1', target: 'queue-standard-1' },
    { source: 'stream-ingest-1', target: 'queue-fifo-1' },
    { source: 'stream-ingest-2', target: 'queue-standard-2' },
    { source: 'stream-ingest-2', target: 'queue-fifo-2' },
    { source: 'stream-ws-1', target: 'queue-standard-5' },
    { source: 'stream-ws-3', target: 'queue-fifo-5' },
    { source: 'stream-ws-6', target: 'queue-standard-5' },
    { source: 'stream-ws-6', target: 'queue-fifo-5' },
    // Queue to Queue routes
    { source: 'queue-standard-3', target: 'queue-standard-4' },
    { source: 'queue-fifo-3', target: 'queue-fifo-4' },
  ],

  // Write targets (channels receiving direct writes)
  writeTargets: [
    'stream-ingest-1', 'stream-ingest-2',
    'stream-ws-1', 'stream-ws-3', 'stream-ws-6',
    'queue-standard-3', 'queue-standard-5',
    'queue-fifo-3', 'queue-fifo-5',
  ],
};
```

### Running with custom settings
```bash
# Default (1 to 100 RPS over 2 minutes)
k6 run loadTest/main.js

# Higher load
k6 run -e START_RPS=10 -e MAX_RPS=500 -e STEADY=10m loadTest/main.js

# Quick smoke test
k6 run -e MAX_RPS=10 -e RAMP_UP=30s -e STEADY=1m loadTest/main.js
```

## K6 Test Structure

```
loadTest/
├── plan.md                 # This plan
├── config.js               # All configurable parameters
├── setup.js                # Channel, producer, route creation
├── main.js                 # Main test orchestration
├── lib/
│   ├── api.js              # API helper functions
│   └── generators.js       # Message payload generators
└── scenarios/
    ├── writers.js          # Message publishing scenarios
    ├── pollers.js          # Queue polling scenarios
    └── websockets.js       # WebSocket consumer scenarios
```

## Decisions Made

- **Payload sizes**: Mixed distribution (50% small, 35% medium, 15% large)
- **WS streams routing**: Yes, also route to queues (dual delivery)
- **Circuit breaker testing**: No - focus on throughput, acknowledge all messages
- **Cleanup**: Manual cleanup via `./development/truncate-tables.sh`

---

## Setup Configuration

### Routing Keys
Each channel will have a single routing key matching the channel name pattern:
- `stream-ingest-1` → routing key: `events`
- `queue-standard-1` → routing key: `events`
- etc.

### Producers (1 per write-target channel)
| Producer Name | Writes To |
|---------------|-----------|
| `producer-stream-ingest-1` | stream-ingest-1 |
| `producer-stream-ingest-2` | stream-ingest-2 |
| `producer-stream-ws-1` | stream-ws-1 |
| `producer-stream-ws-3` | stream-ws-3 |
| `producer-stream-ws-6` | stream-ws-6 |
| `producer-queue-standard-3` | queue-standard-3 |
| `producer-queue-standard-5` | queue-standard-5 |
| `producer-queue-fifo-3` | queue-fifo-3 |
| `producer-queue-fifo-5` | queue-fifo-5 |

### Channel Links (Routes)
| Source Channel | Source Key | Target Channel | Target Key |
|----------------|------------|----------------|------------|
| stream-ingest-1 | events | queue-standard-1 | events |
| stream-ingest-1 | events | queue-fifo-1 | events |
| stream-ingest-2 | events | queue-standard-2 | events |
| stream-ingest-2 | events | queue-fifo-2 | events |
| stream-ws-1 | events | queue-standard-5 | events |
| stream-ws-3 | events | queue-fifo-5 | events |
| stream-ws-6 | events | queue-standard-5 | events |
| stream-ws-6 | events | queue-fifo-5 | events |
| queue-standard-3 | events | queue-standard-4 | events |
| queue-fifo-3 | events | queue-fifo-4 | events |

---

## K6 Test Scenarios

### Scenario 1: Writers (message publishers)
- Publishes messages to all write-target channels
- Distributes load across channels based on configuration
- Uses mixed payload sizes

### Scenario 2: Pollers (queue consumers)
- Polls all 10 queue channels
- Acknowledges messages after polling
- Runs continuously during test

### Scenario 3: WebSocket Consumers
- Connects to stream-ws-1 (1 connection)
- Connects to stream-ws-3 (3 connections)
- Connects to stream-ws-6 (6 connections)
- Receives and counts messages

---

## Implementation Steps

1. [x] Create `config.js` with all configurable parameters (channels, producers, routes, load settings)
2. [x] Create `lib/api.js` with API helper functions (createChannel, createProducer, createLink, publishMessage, pollMessages, ackMessage)
3. [x] Create `lib/generators.js` with payload generators (small, medium, large)
4. [x] Create `setup.js` for test setup (create channels, producers, routes)
5. [x] Create `scenarios/writers.js` for message publishing
6. [x] Create `scenarios/pollers.js` for queue polling
7. [x] Create `scenarios/websockets.js` for WebSocket consumers
8. [x] Create `main.js` to orchestrate all scenarios

---

## Verification

After test completion, verify:
1. All messages published were consumed (no stuck messages in queues)
2. WebSocket connections received expected message counts
3. No errors in application logs
4. Response times within acceptable thresholds

Run with: `k6 run loadTest/main.js`

