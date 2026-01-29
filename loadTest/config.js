// K6 Load Test Configuration for Sintropy Engine

export const config = {
  // API settings
  // baseUrl: __ENV.BASE_URL || 'http://192.168.178.36:8080',
  // wsUrl: __ENV.WS_URL || 'ws://192.168.178.36:8080',
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

  // Channel definitions
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
    { source: 'stream-ingest-1', sourceKey: 'events', target: 'queue-standard-1', targetKey: 'events' },
    { source: 'stream-ingest-1', sourceKey: 'events', target: 'queue-fifo-1', targetKey: 'events' },
    { source: 'stream-ingest-2', sourceKey: 'events', target: 'queue-standard-2', targetKey: 'events' },
    { source: 'stream-ingest-2', sourceKey: 'events', target: 'queue-fifo-2', targetKey: 'events' },
    { source: 'stream-ws-1', sourceKey: 'events', target: 'queue-standard-5', targetKey: 'events' },
    { source: 'stream-ws-3', sourceKey: 'events', target: 'queue-fifo-5', targetKey: 'events' },
    { source: 'stream-ws-6', sourceKey: 'events', target: 'queue-standard-5', targetKey: 'events' },
    { source: 'stream-ws-6', sourceKey: 'events', target: 'queue-fifo-5', targetKey: 'events' },
    // Queue to Queue routes
    { source: 'queue-standard-3', sourceKey: 'events', target: 'queue-standard-4', targetKey: 'events' },
    { source: 'queue-fifo-3', sourceKey: 'events', target: 'queue-fifo-4', targetKey: 'events' },
  ],

  // Write targets (channels receiving direct writes)
  writeTargets: [
    { channel: 'stream-ingest-1', routingKey: 'events', producer: 'producer-stream-ingest-1' },
    { channel: 'stream-ingest-2', routingKey: 'events', producer: 'producer-stream-ingest-2' },
    { channel: 'stream-ws-1', routingKey: 'events', producer: 'producer-stream-ws-1' },
    { channel: 'stream-ws-3', routingKey: 'events', producer: 'producer-stream-ws-3' },
    { channel: 'stream-ws-6', routingKey: 'events', producer: 'producer-stream-ws-6' },
    { channel: 'queue-standard-3', routingKey: 'events', producer: 'producer-queue-standard-3' },
    { channel: 'queue-standard-5', routingKey: 'events', producer: 'producer-queue-standard-5' },
    { channel: 'queue-fifo-3', routingKey: 'events', producer: 'producer-queue-fifo-3' },
    { channel: 'queue-fifo-5', routingKey: 'events', producer: 'producer-queue-fifo-5' },
  ],

  // Poll targets (queues to poll)
  pollTargets: [
    { channel: 'queue-standard-1', routingKey: 'events' },
    { channel: 'queue-standard-2', routingKey: 'events' },
    { channel: 'queue-standard-3', routingKey: 'events' },
    { channel: 'queue-standard-4', routingKey: 'events' },
    { channel: 'queue-standard-5', routingKey: 'events' },
    { channel: 'queue-fifo-1', routingKey: 'events' },
    { channel: 'queue-fifo-2', routingKey: 'events' },
    { channel: 'queue-fifo-3', routingKey: 'events' },
    { channel: 'queue-fifo-4', routingKey: 'events' },
    { channel: 'queue-fifo-5', routingKey: 'events' },
  ],

  // WebSocket targets (streams with consumers)
  wsTargets: [
    { channel: 'stream-ws-1', routingKey: 'events', consumers: 1 },
    { channel: 'stream-ws-3', routingKey: 'events', consumers: 3 },
    { channel: 'stream-ws-6', routingKey: 'events', consumers: 6 },
  ],
};
