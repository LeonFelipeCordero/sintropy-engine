// WebSocket Consumer Scenario
import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { config } from '../config.js';

// Custom metrics
export const wsMessagesReceived = new Counter('ws_messages_received');
export const wsConnectionDuration = new Trend('ws_connection_duration', true);
export const wsConnectionErrors = new Counter('ws_connection_errors');

// WebSocket consumer for a single channel
export function connectWebSocket(channelName, routingKey) {
  const url = `${config.wsUrl}/ws/streaming/${channelName}/${routingKey}`;

  const startTime = Date.now();

  const res = ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      console.log(`WebSocket connected to ${channelName}/${routingKey}`);
    });

    socket.on('message', function (data) {
      try {
        const messages = JSON.parse(data);
        if (Array.isArray(messages)) {
          wsMessagesReceived.add(messages.length);
        } else {
          wsMessagesReceived.add(1);
        }
      } catch (e) {
        // Single message or non-JSON
        wsMessagesReceived.add(1);
      }
    });

    socket.on('error', function (e) {
      console.error(`WebSocket error for ${channelName}: ${e}`);
      wsConnectionErrors.add(1);
    });

    socket.on('close', function () {
      console.log(`WebSocket closed for ${channelName}/${routingKey}`);
    });

    // Keep connection open for the test duration
    socket.setTimeout(function () {
      socket.close();
    }, getTestDurationMs());
  });

  const duration = Date.now() - startTime;
  wsConnectionDuration.add(duration);

  check(res, {
    [`ws ${channelName} connected`]: (r) => r && r.status === 101,
  });
}

// Calculate total test duration in milliseconds
function getTestDurationMs() {
  const parseDuration = (str) => {
    const match = str.match(/^(\d+)(s|m|h)$/);
    if (!match) return 0;
    const [, value, unit] = match;
    const multipliers = { s: 1000, m: 60000, h: 3600000 };
    return parseInt(value) * multipliers[unit];
  };

  const { rampUpDuration, steadyDuration, rampDownDuration } = config.load;
  return parseDuration(rampUpDuration) + parseDuration(steadyDuration) + parseDuration(rampDownDuration);
}

// Connect to stream-ws-1 (1 consumer)
export function wsConsumer1() {
  const target = config.wsTargets.find(t => t.channel === 'stream-ws-1');
  if (target) {
    connectWebSocket(target.channel, target.routingKey);
  }
}

// Connect to stream-ws-3 (3 consumers - run 3 VUs)
export function wsConsumer3() {
  const target = config.wsTargets.find(t => t.channel === 'stream-ws-3');
  if (target) {
    connectWebSocket(target.channel, target.routingKey);
  }
}

// Connect to stream-ws-6 (6 consumers - run 6 VUs)
export function wsConsumer6() {
  const target = config.wsTargets.find(t => t.channel === 'stream-ws-6');
  if (target) {
    connectWebSocket(target.channel, target.routingKey);
  }
}

// Generic WebSocket consumer that connects to all configured streams
export function wsConsumerAll() {
  for (const target of config.wsTargets) {
    for (let i = 0; i < target.consumers; i++) {
      connectWebSocket(target.channel, target.routingKey);
    }
  }
}
