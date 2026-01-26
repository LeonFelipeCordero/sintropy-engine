// Message Payload Generators for K6 Load Tests
import { config } from '../config.js';

// Generate random string of specified length
function randomString(length) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

// Generate small payload (~100 bytes)
function generateSmallPayload() {
  return JSON.stringify({
    id: randomString(8),
    timestamp: Date.now(),
    type: 'small',
    data: randomString(20),
  });
}

// Generate medium payload (~1KB)
function generateMediumPayload() {
  return JSON.stringify({
    id: randomString(8),
    timestamp: Date.now(),
    type: 'medium',
    metadata: {
      source: 'k6-load-test',
      version: '1.0',
      tags: ['test', 'load', 'performance'],
    },
    items: Array.from({ length: 10 }, (_, i) => ({
      index: i,
      value: randomString(50),
      active: Math.random() > 0.5,
    })),
    description: randomString(200),
  });
}

// Generate large payload (~10KB)
function generateLargePayload() {
  return JSON.stringify({
    id: randomString(8),
    timestamp: Date.now(),
    type: 'large',
    metadata: {
      source: 'k6-load-test',
      version: '1.0',
      environment: 'performance',
      tags: ['test', 'load', 'performance', 'stress', 'benchmark'],
      correlation_id: randomString(32),
    },
    items: Array.from({ length: 50 }, (_, i) => ({
      index: i,
      id: randomString(16),
      value: randomString(100),
      nested: {
        field1: randomString(30),
        field2: Math.random() * 1000,
        field3: Math.random() > 0.5,
      },
    })),
    body: randomString(3000),
    footer: {
      checksum: randomString(64),
      processedAt: null,
    },
  });
}

// Generate payload based on configured distribution
export function generatePayload() {
  const rand = Math.random();
  const { small, medium } = config.payloads;

  if (rand < small.weight) {
    return generateSmallPayload();
  } else if (rand < small.weight + medium.weight) {
    return generateMediumPayload();
  } else {
    return generateLargePayload();
  }
}

// Generate headers for messages
export function generateHeaders() {
  return JSON.stringify({
    'x-correlation-id': randomString(16),
    'x-timestamp': Date.now(),
    'x-source': 'k6-load-test',
  });
}
