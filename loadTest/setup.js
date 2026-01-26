// Test Setup - Creates channels, producers, and routes
import { config } from './config.js';
import { createChannel, createProducer, createChannelLink, getChannel } from './lib/api.js';
import { sleep } from 'k6';

export function setupChannels() {
  console.log('Creating STREAM channels...');
  for (const stream of config.channels.streams) {
    createChannel(stream.name, 'STREAM', [stream.routingKey]);
    sleep(0.1);
  }

  console.log('Creating QUEUE STANDARD channels...');
  for (const queue of config.channels.standardQueues) {
    createChannel(queue.name, 'QUEUE', [queue.routingKey], 'STANDARD');
    sleep(0.1);
  }

  console.log('Creating QUEUE FIFO channels...');
  for (const queue of config.channels.fifoQueues) {
    createChannel(queue.name, 'QUEUE', [queue.routingKey], 'FIFO');
    sleep(0.1);
  }
}

export function setupProducers() {
  console.log('Creating producers...');
  for (const target of config.writeTargets) {
    createProducer(target.producer);
    sleep(0.1);
  }
}

export function setupRoutes() {
  console.log('Creating channel links (routes)...');
  for (const route of config.routes) {
    createChannelLink(route.source, route.target, route.sourceKey, route.targetKey);
    sleep(0.1);
  }
}

export function verifySetup() {
  console.log('Verifying setup...');
  let allOk = true;

  // Verify streams
  for (const stream of config.channels.streams) {
    const res = getChannel(stream.name);
    if (res.status !== 200) {
      console.error(`Channel ${stream.name} not found`);
      allOk = false;
    }
  }

  // Verify standard queues
  for (const queue of config.channels.standardQueues) {
    const res = getChannel(queue.name);
    if (res.status !== 200) {
      console.error(`Channel ${queue.name} not found`);
      allOk = false;
    }
  }

  // Verify fifo queues
  for (const queue of config.channels.fifoQueues) {
    const res = getChannel(queue.name);
    if (res.status !== 200) {
      console.error(`Channel ${queue.name} not found`);
      allOk = false;
    }
  }

  if (allOk) {
    console.log('Setup verification complete - all channels exist');
  } else {
    console.error('Setup verification failed - some channels missing');
  }

  return allOk;
}

export function runSetup() {
  console.log('=== Starting Load Test Setup ===');
  setupChannels();
  setupProducers();
  setupRoutes();
  const ok = verifySetup();
  console.log('=== Setup Complete ===');
  return ok;
}
