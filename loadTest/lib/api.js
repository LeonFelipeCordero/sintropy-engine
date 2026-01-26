// API Helper Functions for K6 Load Tests
import http from 'k6/http';
import { check } from 'k6';
import { config } from '../config.js';

const headers = {
  'Content-Type': 'application/json',
};

// Channel operations
export function createChannel(name, channelType, routingKeys, consumptionType = null) {
  const payload = {
    name,
    channelType,
    routingKeys,
  };
  if (consumptionType) {
    payload.consumptionType = consumptionType;
  }

  const res = http.post(`${config.baseUrl}/channels`, JSON.stringify(payload), { headers });
  check(res, {
    [`channel ${name} created`]: (r) => r.status === 201 || r.status === 409,
  });
  return res;
}

export function deleteChannel(name) {
  const res = http.del(`${config.baseUrl}/channels/${name}`, null, { headers });
  check(res, {
    [`channel ${name} deleted`]: (r) => r.status === 204 || r.status === 404,
  });
  return res;
}

export function getChannel(name) {
  return http.get(`${config.baseUrl}/channels/${name}`, { headers });
}

// Producer operations
export function createProducer(name) {
  const payload = { name };
  const res = http.post(`${config.baseUrl}/producers`, JSON.stringify(payload), { headers });
  check(res, {
    [`producer ${name} created`]: (r) => r.status === 201 || r.status === 409,
  });
  return res;
}

export function deleteProducer(name) {
  const res = http.del(`${config.baseUrl}/producers/${name}`, null, { headers });
  check(res, {
    [`producer ${name} deleted`]: (r) => r.status === 204 || r.status === 404,
  });
  return res;
}

// Channel link operations
export function createChannelLink(sourceChannelName, targetChannelName, sourceRoutingKey, targetRoutingKey) {
  const payload = {
    sourceChannelName,
    targetChannelName,
    sourceRoutingKey,
    targetRoutingKey,
  };
  const res = http.post(`${config.baseUrl}/channels/links`, JSON.stringify(payload), { headers });
  check(res, {
    [`link ${sourceChannelName} -> ${targetChannelName} created`]: (r) => r.status === 201 || r.status === 409,
  });
  return res;
}

export function deleteChannelLink(linkId) {
  const res = http.del(`${config.baseUrl}/channels/links/${linkId}`, null, { headers });
  return res;
}

export function getOutgoingLinks(channelName) {
  return http.get(`${config.baseUrl}/channels/${channelName}/links/outgoing`, { headers });
}

// Message operations
export function publishMessage(channelName, producerName, routingKey, message, messageHeaders = '{}') {
  const payload = {
    channelName,
    producerName,
    routingKey,
    message,
    headers: messageHeaders,
  };
  const res = http.post(`${config.baseUrl}/producers/messages`, JSON.stringify(payload), { headers });
  return res;
}

export function pollMessages(channelName, routingKey, pollingCount = 10) {
  const payload = {
    channelName,
    routingKey,
    pollingCount,
  };
  const res = http.post(`${config.baseUrl}/queues/poll`, JSON.stringify(payload), { headers });
  return res;
}

export function ackMessage(messageId) {
  const res = http.del(`${config.baseUrl}/queues/messages/${messageId}`, null, { headers });
  return res;
}

export function ackMessagesBulk(messageIds) {
  const payload = { messageIds };
  const res = http.post(`${config.baseUrl}/queues/messages/dequeue/bulk`, JSON.stringify(payload), { headers });
  return res;
}

export function markMessageFailed(messageId) {
  const res = http.post(`${config.baseUrl}/queues/messages/${messageId}/failed`, null, { headers });
  return res;
}
