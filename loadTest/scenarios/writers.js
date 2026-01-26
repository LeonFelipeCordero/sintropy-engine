// Message Publishing Scenario
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { config } from '../config.js';
import { publishMessage } from '../lib/api.js';
import { generatePayload, generateHeaders } from '../lib/generators.js';

// Custom metrics
export const publishDuration = new Trend('publish_duration', true);
export const publishSuccess = new Counter('publish_success');
export const publishFailure = new Counter('publish_failure');

export function writeMessages() {
  // Select a random write target
  const targetIndex = Math.floor(Math.random() * config.writeTargets.length);
  const target = config.writeTargets[targetIndex];

  const payload = generatePayload();
  const headers = generateHeaders();

  const startTime = Date.now();
  const res = publishMessage(
    target.channel,
    target.producer,
    target.routingKey,
    payload,
    headers
  );
  const duration = Date.now() - startTime;

  publishDuration.add(duration);

  const success = check(res, {
    'message published': (r) => r.status === 201,
  });

  if (success) {
    publishSuccess.add(1);
  } else {
    publishFailure.add(1);
    if (res.status !== 201) {
      console.error(`Publish failed: ${res.status} - ${res.body}`);
    }
  }
}
