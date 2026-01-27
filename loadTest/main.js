// K6 Load Test - Main Orchestration
// Sintropy Engine Message Broker Load Test
//
// Usage:
//   k6 run loadTest/main.js                           # Default settings
//   k6 run -e MAX_RPS=500 loadTest/main.js            # Higher load
//   k6 run -e MAX_RPS=10 -e STEADY=1m loadTest/main.js  # Quick smoke test

import { config } from './config.js';
import { runSetup } from './setup.js';
import { writeMessages, publishDuration, publishSuccess, publishFailure } from './scenarios/writers.js';
import { pollQueues, pollDuration, messagesPolled, messagesAcked, pollErrors } from './scenarios/pollers.js';
import { wsConsumer1, wsConsumer3, wsConsumer6, wsMessagesReceived, wsConnectionErrors } from './scenarios/websockets.js';

// Export metrics for k6
export { publishDuration, publishSuccess, publishFailure };
export { pollDuration, messagesPolled, messagesAcked, pollErrors };
export { wsMessagesReceived, wsConnectionErrors };

// Test options
export const options = {
  scenarios: {
    // Message Writers - ramp up to configured RPS
    writers: {
      executor: 'ramping-arrival-rate',
      startRate: config.load.startRps,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: config.load.rampUpDuration, target: config.load.maxRps },
        { duration: config.load.steadyDuration, target: config.load.maxRps },
        { duration: config.load.rampDownDuration, target: 0 },
      ],
      exec: 'writers',
    },

    // Queue Pollers - constant VUs polling continuously
    pollers: {
      executor: 'constant-vus',
      vus: 20,
      duration: getTotalDuration(),
      exec: 'pollers',
    },

    // WebSocket Consumers - one VU per connection
    ws_consumer_1: {
      executor: 'constant-vus',
      vus: 1,
      duration: getTotalDuration(),
      exec: 'wsConsumer1',
    },
    ws_consumer_3: {
      executor: 'constant-vus',
      vus: 3,
      duration: getTotalDuration(),
      exec: 'wsConsumer3',
    },
    ws_consumer_6: {
      executor: 'constant-vus',
      vus: 6,
      duration: getTotalDuration(),
      exec: 'wsConsumer6',
    },
  },

  thresholds: {
    // Publishing thresholds
    'publish_duration': ['p(95)<500', 'p(99)<1000'],
    'publish_success': ['count>0'],

    // Polling thresholds
    'poll_duration': ['p(95)<500', 'p(99)<1000'],

    // WebSocket thresholds
    'ws_connection_errors': ['count<10'],

    // HTTP request thresholds
    'http_req_duration': ['p(95)<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};

// Calculate total test duration string
function getTotalDuration() {
  const parseDuration = (str) => {
    const match = str.match(/^(\d+)(s|m|h)$/);
    if (!match) return 0;
    const [, value, unit] = match;
    const multipliers = { s: 1, m: 60, h: 3600 };
    return parseInt(value) * multipliers[unit];
  };

  const totalSeconds =
    parseDuration(config.load.rampUpDuration) +
    parseDuration(config.load.steadyDuration) +
    parseDuration(config.load.rampDownDuration);

  // Return as duration string
  if (totalSeconds >= 3600) {
    return `${Math.ceil(totalSeconds / 3600)}h`;
  } else if (totalSeconds >= 60) {
    return `${Math.ceil(totalSeconds / 60)}m`;
  } else {
    return `${totalSeconds}s`;
  }
}

// Setup function - runs once before test
export function setup() {
  console.log('='.repeat(60));
  console.log('Sintropy Engine Load Test');
  console.log('='.repeat(60));
  console.log(`Base URL: ${config.baseUrl}`);
  console.log(`WebSocket URL: ${config.wsUrl}`);
  console.log(`Load Profile: ${config.load.startRps} -> ${config.load.maxRps} RPS`);
  console.log(`Duration: ${config.load.rampUpDuration} ramp + ${config.load.steadyDuration} steady + ${config.load.rampDownDuration} ramp down`);
  console.log('='.repeat(60));

  const setupOk = runSetup();

  return { setupOk };
}

// Teardown function - runs once after test
export function teardown(data) {
  console.log('='.repeat(60));
  console.log('Test Complete');
  console.log('To clean up data, run: ./development/truncate-tables.sh');
  console.log('='.repeat(60));
}

// Scenario executors
export function writers() {
  writeMessages();
}

export function pollers() {
  pollQueues();
}

export { wsConsumer1, wsConsumer3, wsConsumer6 };

// Default function (not used with scenarios, but required by k6)
export default function () {
  writeMessages();
}
