// Test Teardown - Cleans up channels, producers, and routes
import { config } from './config.js';
import { deleteChannel, deleteProducer, getOutgoingLinks, deleteChannelLink } from './lib/api.js';
import { sleep } from 'k6';

export function teardownRoutes() {
  console.log('Deleting channel links (routes)...');
  // Get and delete outgoing links for each source channel
  const sourceChannels = [...new Set(config.routes.map(r => r.source))];
  for (const channelName of sourceChannels) {
    const res = getOutgoingLinks(channelName);
    if (res.status === 200) {
      try {
        const links = JSON.parse(res.body);
        for (const link of links) {
          deleteChannelLink(link.channelLinkId);
          sleep(0.05);
        }
      } catch (e) {
        console.error(`Failed to parse links for ${channelName}: ${e}`);
      }
    }
    sleep(0.1);
  }
}

export function teardownProducers() {
  console.log('Deleting producers...');
  for (const target of config.writeTargets) {
    deleteProducer(target.producer);
    sleep(0.05);
  }
}

export function teardownChannels() {
  console.log('Deleting STREAM channels...');
  for (const stream of config.channels.streams) {
    deleteChannel(stream.name);
    sleep(0.05);
  }

  console.log('Deleting QUEUE STANDARD channels...');
  for (const queue of config.channels.standardQueues) {
    deleteChannel(queue.name);
    sleep(0.05);
  }

  console.log('Deleting QUEUE FIFO channels...');
  for (const queue of config.channels.fifoQueues) {
    deleteChannel(queue.name);
    sleep(0.05);
  }
}

export function runTeardown() {
  console.log('=== Starting Load Test Teardown ===');
  teardownRoutes();
  teardownProducers();
  teardownChannels();
  console.log('=== Teardown Complete ===');
}
