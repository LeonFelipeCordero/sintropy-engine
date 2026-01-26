// Queue Polling Scenario
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { config } from '../config.js';
import { pollMessages, ackMessagesBulk } from '../lib/api.js';

// Custom metrics
export const pollDuration = new Trend('poll_duration', true);
export const ackDuration = new Trend('ack_duration', true);
export const messagesPolled = new Counter('messages_polled');
export const messagesAcked = new Counter('messages_acked');
export const pollErrors = new Counter('poll_errors');

export function pollQueues() {
  // Select a random poll target
  const targetIndex = Math.floor(Math.random() * config.pollTargets.length);
  const target = config.pollTargets[targetIndex];

  const startTime = Date.now();
  const res = pollMessages(
    target.channel,
    target.routingKey,
    config.polling.batchSize
  );
  const duration = Date.now() - startTime;

  pollDuration.add(duration);

  const pollSuccess = check(res, {
    'poll successful': (r) => r.status === 200,
  });

  if (!pollSuccess) {
    pollErrors.add(1);
    console.error(`Poll failed for ${target.channel}: ${res.status} - ${res.body}`);
    return;
  }

  try {
    const messages = JSON.parse(res.body);
    if (messages && messages.length > 0) {
      messagesPolled.add(messages.length);

      // Acknowledge all messages in bulk
      const messageIds = messages.map(m => m.messageId);

      const ackStart = Date.now();
      const ackRes = ackMessagesBulk(messageIds);
      const ackTime = Date.now() - ackStart;

      ackDuration.add(ackTime);

      const ackSuccess = check(ackRes, {
        'ack successful': (r) => r.status === 200,
      });

      if (ackSuccess) {
        messagesAcked.add(messages.length);
      }
    }
  } catch (e) {
    console.error(`Failed to parse poll response: ${e}`);
  }

  // Small sleep between polls
  sleep(config.polling.intervalMs / 1000);
}

// Poll all queues sequentially (for comprehensive coverage)
export function pollAllQueues() {
  for (const target of config.pollTargets) {
    const startTime = Date.now();
    const res = pollMessages(
      target.channel,
      target.routingKey,
      config.polling.batchSize
    );
    const duration = Date.now() - startTime;

    pollDuration.add(duration);

    if (res.status !== 200) {
      pollErrors.add(1);
      continue;
    }

    try {
      const messages = JSON.parse(res.body);
      if (messages && messages.length > 0) {
        messagesPolled.add(messages.length);

        const messageIds = messages.map(m => m.messageId);
        const ackRes = ackMessagesBulk(messageIds);

        if (ackRes.status === 200) {
          messagesAcked.add(messages.length);
        }
      }
    } catch (e) {
      // ignore parse errors
    }
  }

  sleep(config.polling.intervalMs / 1000);
}
