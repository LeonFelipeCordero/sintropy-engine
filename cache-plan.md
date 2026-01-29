# Cache Layer Implementation Plan

## Goal

Reduce database load by caching `Channel` and `Producer` objects between services and repositories. These objects are fetched on every publish and poll operation, creating amplified database queries (4-table JOINs for channels).

---

## Requirements

1. **GC-friendly**: Objects must be immutable; return the same cached instance (no object creation on cache hit)
2. **L1 cache proximity**: Data structures should be CPU cache-line friendly
3. **Event-driven invalidation**: Invalidate immediately when data changes
4. **Single instance**: Local in-memory cache (no distributed cache needed)
5. **Warm-up at startup**: Pre-populate cache when application starts
6. **Metrics**: Track cache misses, invalidations, and cache size

---

## Current Hot Paths

| Operation | Queries | Tables Joined |
|-----------|---------|---------------|
| Publish message | Channel lookup + Producer lookup | channels + routing_keys + queues + circuit_breakers |
| Poll messages | Channel lookup + Producer batch | Same 4-table join |
| Circuit breaker check | Channel lookup | Same 4-table join |

---

## Proposed Architecture

### Cache Layer Structure

```
┌─────────────┐     ┌─────────────────┐     ┌────────────────┐
│   Service   │ ──▶ │   CacheLayer    │ ──▶ │   Repository   │
└─────────────┘     └─────────────────┘     └────────────────┘
                           │
                    ┌──────┴──────┐
                    │  In-Memory  │
                    │    Store    │
                    └─────────────┘
```

### Component: `ChannelCache`

```kotlin
@ApplicationScoped
class ChannelCache(
    private val channelRepository: ChannelRepository,
    private val meterRegistry: MeterRegistry
) {
    // Primary lookup: by name (most common in publish/poll)
    private val byName: ConcurrentHashMap<String, Channel> = ConcurrentHashMap()

    // Index: by ID (for batch lookups and invalidation)
    private val byId: ConcurrentHashMap<UUID, Channel> = ConcurrentHashMap()

    // Metrics
    private val missCounter: Counter
    private val invalidationCounter: Counter
    private val sizeGauge: AtomicInteger
}
```

**Key design decisions:**
- `ConcurrentHashMap` returns the **same object reference** (no copies, GC-friendly)
- Multiple indexes for O(1) lookup on all access patterns
- No separate `byNameAndRoutingKey` map - routing key validation happens in-memory (see section below)

### Component: `ProducerCache`

```kotlin
@ApplicationScoped
class ProducerCache(
    private val producerRepository: ProducerRepository,
    private val meterRegistry: MeterRegistry
) {
    private val byName: ConcurrentHashMap<String, Producer> = ConcurrentHashMap()
    private val byId: ConcurrentHashMap<UUID, Producer> = ConcurrentHashMap()

    // Metrics
    private val missCounter: Counter
    private val invalidationCounter: Counter
    private val sizeGauge: AtomicInteger
}
```

---

## Routing Key Validation Strategy

### Why This Matters

The current `findByNameAndRoutingKeyStrict(channelName, routingKey)` method does two things:
1. Fetches the channel by name
2. Validates that the routing key exists for that channel

**Current behavior** (database query):
```kotlin
// ChannelRepository.findByNameAndRoutingKey()
.where(CHANNELS.NAME.eq(name))
.and(ROUTING_KEYS.ROUTING_KEY.eq(routingKey))  // DB validates routing key exists
```

If the routing key doesn't exist, the query returns no results → service throws `ChannelNotFoundException`.

**The problem with per-routing-key caching:**
- A channel with 10 routing keys would create 10 cache entries
- Each entry stores the **same** Channel object reference, but the map has 10 keys
- Memory overhead scales with `channels × routing_keys`
- Invalidation becomes complex: must invalidate all routing key entries when channel changes

**The in-memory validation approach:**
- Cache **one entry per channel** (by name)
- When `findByNameAndRoutingKeyStrict` is called:
  1. Get channel from cache by name
  2. Check `channel.routingKeys.contains(routingKey)` in-memory
  3. Throw if not found, return channel if found

```kotlin
fun findByNameAndRoutingKeyStrict(name: String, routingKey: String): Channel {
    val channel = getByName(name)
        ?: throw ChannelNotFoundException("Channel $name not found")

    if (!channel.routingKeys.contains(routingKey)) {
        throw RoutingKeyNotFoundException("Routing key $routingKey not found in channel $name")
    }

    return channel  // Same object instance, no allocation
}
```

**Benefits:**
- Fewer cache entries (1 per channel, not 1 per channel×routingKey)
- Simpler invalidation (invalidate by channel name only)
- Same object returned regardless of which routing key was requested
- `List.contains()` on small lists (~1-10 routing keys) is fast, likely faster than HashMap lookup

**Trade-off:**
- Slightly more CPU for validation vs. direct HashMap lookup
- For channels with hundreds of routing keys, could convert `routingKeys` to a `Set` for O(1) contains

---

## L1 Cache Optimization Strategies

### 1. Compact Object Layout

Current `Channel` class has:
- `UUID` (16 bytes)
- `String` (reference + char array)
- `MutableList<String>` (ArrayList with array + references)
- `List<RoutingKeyCircuitState>` (nested objects)

**Optimization approach: Keep data classes, optimize access patterns**
- Store channels in `ConcurrentHashMap` which provides good locality for frequently accessed entries
- Use `@JvmField` annotations to avoid getter overhead on hot paths
- Channel objects are small enough to fit in L1/L2 cache (typically <1KB each)

### 2. HashMap Sizing

Pre-size maps based on expected channel count to avoid rehashing:
```kotlin
ConcurrentHashMap<String, Channel>(initialCapacity = 256, loadFactor = 0.75f)
```

### 3. Array-Based Routing Keys

Consider changing `routingKeys: MutableList<String>` to `routingKeys: Array<String>`:
- Arrays have better memory locality than ArrayList
- Fixed size after creation (immutable semantics)
- Enables sequential memory access for `contains()` checks

---

## Cache Warm-Up at Startup

```kotlin
@ApplicationScoped
class CacheWarmupService(
    private val channelCache: ChannelCache,
    private val producerCache: ProducerCache
) {
    fun onStart(@Observes event: StartupEvent) {
        log.info("Warming up caches...")

        val channels = channelRepository.findAll()
        channels.forEach { channelCache.put(it) }
        log.info("Loaded ${channels.size} channels into cache")

        val producers = producerRepository.findAll()
        producers.forEach { producerCache.put(it) }
        log.info("Loaded ${producers.size} producers into cache")
    }
}
```

**Startup behavior:**
- Application blocks until cache is fully populated
- First request is served from cache (no cold-start penalty)
- If database is unavailable at startup, application fails fast

---

## Cache Invalidation Strategy

### Event-Driven Hooks

Invalidate cache entries when data changes in service layer:

```kotlin
// In ChannelService
fun create(channel: Channel): Channel {
    val created = channelRepository.create(channel)
    channelCache.put(created)  // Add to cache
    return created
}

fun addRoutingKey(channelId: UUID, routingKey: String): Channel {
    val updated = channelRepository.addRoutingKey(channelId, routingKey)
    channelCache.invalidate(channelId)  // Remove stale entry
    channelCache.put(updated)           // Add fresh entry
    return updated
}

fun delete(channelId: UUID) {
    val channel = channelCache.getById(channelId)
    channelCache.invalidate(channelId)  // Remove from cache
    channelRepository.delete(channelId)
}
```

### Circuit Breaker State Changes

Circuit state changes via `CircuitBreakerService` methods:

```kotlin
// In CircuitBreakerService
fun closeCircuit(channelName: String, routingKey: String) {
    val channel = channelService.findByNameAndRoutingKeyStrict(channelName, routingKey)
    circuitBreakerRepository.closeCircuit(channel.channelId!!, routingKey)

    // Invalidate and refresh channel cache (circuit state changed)
    channelCache.invalidate(channel.channelId!!)
    val refreshed = channelRepository.findById(channel.channelId!!)
    refreshed?.let { channelCache.put(it) }
}

fun openCircuit(channelName: String, routingKey: String) {
    // Same pattern: invalidate + refresh
}
```

**Note:** Circuit also opens via database trigger when FIFO message fails. This trigger-based change won't invalidate the cache automatically. Options:
1. Accept brief staleness (circuit opens slightly delayed in cache)
2. Use PostgreSQL LISTEN/NOTIFY to receive trigger events
3. On publish failure path, refresh the channel cache entry

---

## Metrics

### Counters and Gauges

```kotlin
// In ChannelCache
private val missCounter = meterRegistry.counter("cache.channel.miss")
private val invalidationCounter = meterRegistry.counter("cache.channel.invalidation")
private val sizeGauge = meterRegistry.gauge("cache.channel.size", AtomicInteger(0))

// In ProducerCache
private val missCounter = meterRegistry.counter("cache.producer.miss")
private val invalidationCounter = meterRegistry.counter("cache.producer.invalidation")
private val sizeGauge = meterRegistry.gauge("cache.producer.size", AtomicInteger(0))
```

### Metric Events

| Event | Metric Incremented |
|-------|-------------------|
| Cache lookup returns null, DB queried | `cache.{type}.miss` |
| `invalidate()` called | `cache.{type}.invalidation` |
| After any put/invalidate | Update `cache.{type}.size` gauge |

### Usage in Code

```kotlin
fun getByName(name: String): Channel? {
    val cached = byName[name]
    if (cached != null) {
        return cached
    }

    missCounter.increment()
    return channelRepository.findByName(name)?.also { put(it) }
}

fun invalidate(channelId: UUID) {
    val channel = byId.remove(channelId)
    if (channel != null) {
        byName.remove(channel.name)
        invalidationCounter.increment()
        sizeGauge.decrementAndGet()
    }
}
```

---

## API / Interface Design

### ChannelCache Interface

```kotlin
interface ChannelCache {
    // Lookups (return same object instance, no allocation)
    fun getByName(name: String): Channel?
    fun getByNameAndRoutingKeyStrict(name: String, routingKey: String): Channel
    fun getById(id: UUID): Channel?
    fun getByIds(ids: Set<UUID>): List<Channel>

    // Mutations
    fun put(channel: Channel)
    fun invalidate(channelId: UUID)
    fun clear()

    // Metrics
    fun size(): Int
}
```

### ProducerCache Interface

```kotlin
interface ProducerCache {
    fun getByName(name: String): Producer?
    fun getById(id: UUID): Producer?
    fun getByIds(ids: Set<UUID>): List<Producer>

    fun put(producer: Producer)
    fun invalidate(producerId: UUID)
    fun clear()

    fun size(): Int
}
```

---

## Service Layer Changes

### Before (Current)

```kotlin
// ChannelService.kt
fun findByName(name: String): Channel? = channelRepository.findByName(name)

fun findByNameAndRoutingKeyStrict(name: String, routingKey: String): Channel {
    return channelRepository.findByNameAndRoutingKey(name, routingKey)
        ?: throw ChannelNotFoundException("...")
}
```

### After (With Cache)

```kotlin
// ChannelService.kt
fun findByName(name: String): Channel? = channelCache.getByName(name)

fun findByNameAndRoutingKeyStrict(name: String, routingKey: String): Channel {
    return channelCache.getByNameAndRoutingKeyStrict(name, routingKey)
}
```

The service layer becomes thinner - cache handles DB fallback internally.

---

## Implementation Steps

### Phase 1: Core Cache Infrastructure
1. Create `ChannelCache` interface and implementation class
2. Create `ProducerCache` interface and implementation class
3. Add Micrometer metrics (miss, invalidation, size)
4. Create `CacheWarmupService` with `@Observes StartupEvent`

### Phase 2: Integrate with Services
5. Modify `ChannelService` to use `ChannelCache` for all read operations
6. Modify `ProducerService` to use `ProducerCache` for all read operations
7. Add invalidation hooks to create/update/delete methods in both services

### Phase 3: Circuit Breaker Integration
8. Hook `CircuitBreakerService.closeCircuit()` to invalidate + refresh channel cache
9. Hook `CircuitBreakerService.openCircuit()` to invalidate + refresh channel cache
10. Handle trigger-based circuit opens (decide on staleness vs LISTEN/NOTIFY)

### Phase 4: Testing
11. Unit tests for cache hit/miss behavior
12. Unit tests for invalidation correctness
13. Unit tests for routing key in-memory validation
14. Integration tests verifying DB is not hit on cache hit
15. Integration tests for warm-up behavior

### Phase 5: Observability
16. Add Grafana dashboard panel for cache metrics
17. Add alerts for unusual miss rates

---

## Files to Create/Modify

### New Files
- `src/main/kotlin/com/ph/sintropyengine/broker/shared/cache/ChannelCache.kt`
- `src/main/kotlin/com/ph/sintropyengine/broker/shared/cache/ProducerCache.kt`
- `src/main/kotlin/com/ph/sintropyengine/broker/shared/cache/CacheWarmupService.kt`
- `src/test/kotlin/com/ph/sintropyengine/broker/shared/cache/ChannelCacheTest.kt`
- `src/test/kotlin/com/ph/sintropyengine/broker/shared/cache/ProducerCacheTest.kt`

### Modified Files
- `src/main/kotlin/com/ph/sintropyengine/broker/channel/service/ChannelService.kt`
- `src/main/kotlin/com/ph/sintropyengine/broker/producer/service/ProducerService.kt`
- `src/main/kotlin/com/ph/sintropyengine/broker/consumption/service/CircuitBreakerService.kt`

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Stale circuit state from DB triggers | Option 1: Accept brief staleness. Option 2: LISTEN/NOTIFY. Option 3: Refresh on publish path |
| Cache inconsistency on crash | Cache is warm-up only; DB is source of truth; restart repopulates |
| Memory growth if many channels | Monitor `cache.*.size` gauge; add eviction policy if needed later |

---

## Questions for Discussion

1. **Trigger-based circuit opens**: Which approach do you prefer?
   - Accept staleness (simplest)
   - PostgreSQL LISTEN/NOTIFY (most correct)
   - Refresh on publish failure path (middle ground)

2. **Should cache classes be interfaces + implementations** (for easier testing/mocking) or just concrete classes?

3. **Package location**: `broker/shared/cache/` or separate `broker/cache/` domain?
