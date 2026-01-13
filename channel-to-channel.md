# Channel Linking Feature Plan

## Overview
Create a feature to connect channels to channels with directional linking. Messages published to a source channel can be forwarded to linked target channels.

## Connection Constraints

### Compatibility Matrix (Source → Target)
| Source Type        | → Standard Queue | → FIFO Queue | → Stream |
|--------------------|------------------|--------------|----------|
| **Standard Queue** | ✓                | ✗            | ✗        |
| **FIFO Queue**     | ✓                | ✓            | ✓        |
| **Stream**         | ✓                | ✓            | ✓        |

**Rationale**: Standard Queues don't guarantee message ordering. FIFO Queues and Streams require ordered delivery. Therefore, Standard Queues can only connect to other Standard Queues.

## Implementation Steps

### 1. Database Migration
Create `V1.7__create-channel-links-table.sql`:
- `channel_link_id` (UUID, PK)
- `source_channel_id` (UUID, FK → channels)
- `target_channel_id` (UUID, FK → channels)
- `source_routing_key` (VARCHAR) - which routing key to listen from
- `target_routing_key` (VARCHAR) - which routing key to publish to
- `created_at` (TIMESTAMP)
- `updated_at` (TIMESTAMP)
- `enabled` (BOOLEAN, default true)
- Unique constraint on (source_channel_id, target_channel_id, source_routing_key, target_routing_key)

### 2. Domain Model
Create `ChannelLink.kt` in `broker/channel/model/`:
```kotlin
data class ChannelLink(
    val channelLinkId: UUID?,
    val sourceChannelId: UUID,
    val targetChannelId: UUID,
    val sourceRoutingKey: String,
    val targetRoutingKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val enabled: Boolean = true
)
```

### 3. Repository Layer
Create `ChannelLinkRepository.kt` in `broker/channel/repository/`:
- `create(channelLink: ChannelLink): ChannelLink`
- `findById(channelLinkId: UUID): ChannelLink?`
- `findBySourceChannelId(sourceChannelId: UUID): List<ChannelLink>`
- `findByTargetChannelId(targetChannelId: UUID): List<ChannelLink>`
- `findBySourceChannelIdAndRoutingKey(sourceChannelId: UUID, routingKey: String): List<ChannelLink>`
- `delete(channelLinkId: UUID)`
- `setEnabled(channelLinkId: UUID, enabled: Boolean)`

### 4. Service Layer
Create `ChannelLinkService.kt` in `broker/channel/service/`:
- `linkChannels(sourceChannelName: String, targetChannelName: String, sourceRoutingKey: String, targetRoutingKey: String): ChannelLink`
  - Validate both channels exist
  - Validate routing keys exist in respective channels
  - **Validate compatibility**: Check if source can connect to target
  - Create the link
- `unlinkChannels(channelLinkId: UUID)`
- `getLinksFromChannel(channelName: String): List<ChannelLink>`
- `getLinksToChannel(channelName: String): List<ChannelLink>`
- `enableLink(channelLinkId: UUID)`
- `disableLink(channelLinkId: UUID)`

**Validation Logic**:
```kotlin
fun validateLinkCompatibility(source: Channel, target: Channel) {
    val sourceIsStandard = source.channelType == QUEUE && source.consumptionType == STANDARD
    val targetRequiresOrdering = target.channelType == STREAM ||
                                  (target.channelType == QUEUE && target.consumptionType == FIFO)

    if (sourceIsStandard && targetRequiresOrdering) {
        throw IllegalStateException(
            "Cannot link Standard Queue to ${target.channelType}/${target.consumptionType}: " +
            "Standard Queues don't guarantee message ordering"
        )
    }
}
```

### 5. API Layer
Create `ChannelLinkApi.kt` in `broker/channel/api/`:

**Endpoints**:
- `POST /channels/links` - Create a link
  - Request: `{ sourceChannelName, targetChannelName, sourceRoutingKey, targetRoutingKey }`
  - Response: `201 Created` with ChannelLink
- `GET /channels/links/{linkId}` - Get link by ID
- `GET /channels/{channelName}/links/outgoing` - Get all outgoing links from channel
- `GET /channels/{channelName}/links/incoming` - Get all incoming links to channel
- `DELETE /channels/links/{linkId}` - Delete a link
- `PUT /channels/links/{linkId}/enable` - Enable a link
- `PUT /channels/links/{linkId}/disable` - Disable a link

### 6. Message Forwarding (Future Enhancement)
After links are established, integrate with message publishing:
- When a message is published, check for active links from that channel/routing key
- Forward the message to all linked target channels with appropriate routing keys
- This will be handled in `ProducerService` or a new `MessageForwardingService`

### 7. Tests
Create `ChannelLinkApiTest.kt` and `ChannelLinkServiceTest.kt`:
- Test successful linking of compatible channel types
- Test rejection of incompatible links (Standard → FIFO, Standard → Stream)
- Test CRUD operations
- Test enable/disable functionality
- Test validation of channel/routing key existence

## Files to Create/Modify

### New Files:
1. `src/main/resources/db/migration/V1.7__create-channel-links-table.sql`
2. `src/main/kotlin/com/ph/sintropyengine/broker/channel/model/ChannelLink.kt`
3. `src/main/kotlin/com/ph/sintropyengine/broker/channel/repository/ChannelLinkRepository.kt`
4. `src/main/kotlin/com/ph/sintropyengine/broker/channel/service/ChannelLinkService.kt`
5. `src/main/kotlin/com/ph/sintropyengine/broker/channel/api/ChannelLinkApi.kt`
6. `src/test/kotlin/com/ph/sintropyengine/broker/channel/api/ChannelLinkApiTest.kt`
7. `src/test/kotlin/com/ph/sintropyengine/broker/channel/service/ChannelLinkServiceTest.kt`

### Modified Files:
- `IntegrationTestBase.kt` - Add helper methods for creating channel links in tests
