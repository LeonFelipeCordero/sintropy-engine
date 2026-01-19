# Code Review: Observability Implementation

## Overview

This changeset adds comprehensive observability capabilities to Sintropy Engine, including:
- OpenTelemetry tracing and logging integration
- Micrometer metrics with OTLP export
- Grafana LGTM stack for local development
- Structured logging improvements across multiple components

## Files Changed

| File | Type |
|------|------|
| `build.gradle.kts` | Dependencies |
| `development/docker-compose.yaml` | Infrastructure |
| `ObservabilityService.kt` | **New file** |
| `MessageStreamingApi.kt` | Modified |
| `QueueApi.kt` | Modified |
| `CircuitBreakerService.kt` | Modified |
| `ConnectionRouter.kt` | Modified |
| `DeadLetterQueueService.kt` | Modified |
| `PGReplicationController.kt` | Modified |
| `ProducerApi.kt` | Modified |
| `application.yml` | Configuration |

---

## Issues Found and Fixed

### High Priority - FIXED

#### 1. ✅ Metric Registration Pattern Creates Memory Leak Risk
**File:** `ObservabilityService.kt`

**Problem:** Every call to `Counter.builder(...).register(registry)` was inefficient.

**Fix Applied:** Added `ConcurrentHashMap` caching for counters and timers via `getOrCreateCounter()` helper method. Counters are now cached by their name and tag combination.

#### 2. ✅ Hardcoded "unknown" Channel Name
**File:** `DeadLetterQueueService.kt`

**Problem:** Metrics had `channel=unknown` tag, losing valuable context.

**Fix Applied:**
- `recoverMessage()` now looks up the channel by ID before recording metrics
- `recoverMessages()` now fetches channels by IDs and groups metrics appropriately

#### 3. ✅ Missing `recordMessageFailed` and `recordMessageDequeued` Calls
**File:** `QueueApi.kt`

**Problem:** `markAsFailed` and `dequeue` endpoints had no observability.

**Fix Applied:**
- Added `MessageRepository` injection to QueueApi
- Both endpoints now look up message info and record appropriate metrics
- Improved logging to include channel and routing key context

#### 4. ✅ `timeOperation` NPE Risk
**File:** `ObservabilityService.kt`

**Problem:** `timer.recordCallable(block)!!` could NPE if block returns null.

**Fix Applied:** Changed return type to `T?` (nullable) and removed the `!!` assertion.

#### 5. ✅ Missing Newline at End of `application.yml`
**Fix Applied:** Added trailing newline for POSIX compliance.

---

## Issues Acknowledged - No Fix Needed

### Circuit Breaker Metrics (`recordCircuitOpened`, `recordMessageToDlq`)

**Context:** Circuit breakers open via PostgreSQL database trigger (`open_circuit_on_failed_message_delete` in V1.9 migration), not application code. Messages are moved to DLQ within the same trigger.

**Assessment:** The 30-second polling approach (`syncOpenCircuitsGauge()`) is the correct design for this architecture. Real-time `recordCircuitOpened()` calls would require:
- A PostgreSQL NOTIFY/LISTEN mechanism, or
- Polling the DB after every failed message deletion

The current polling approach provides acceptable accuracy for operational metrics while keeping the architecture simple.

**Recommendation:** Consider adding a comment in `CircuitBreakerService` explaining why the gauge uses polling instead of event-driven updates.

---

## Remaining Considerations

### Low Priority

#### 1. Inconsistent Log Levels
- `QueueApi.kt`: Logging poll results at INFO level for every poll could be noisy in production
- Consider making high-frequency operation logs DEBUG level

#### 2. ConnectionRouter Memory
The `connectionMetadata` map cleanup logic appears correct, but ensure edge cases are tested.

---

## Positive Observations

1. **Good structured logging format** - Using `[key=value, key2=value2]` pattern consistently
2. **Proper test profile config** - OTEL disabled in tests (`%test` profile)
3. **Clean separation** - `ObservabilityService` as a central abstraction
4. **Appropriate use of `@Startup`** - Ensures metrics are initialized on boot
5. **Docker compose addition** - LGTM stack is a good choice for local observability
6. **Good metric naming** - Following `namespace.entity.action.unit` convention

---

## Security Considerations

- **Grafana password**: `GF_SECURITY_ADMIN_PASSWORD=admin` is acceptable for development but ensure production uses secure credentials
- No sensitive data appears to be logged or exposed in metrics

---

## Summary of Fixes Applied

| Issue | Status |
|-------|--------|
| Counter caching in ObservabilityService | ✅ Fixed |
| "unknown" channel name in DLQ metrics | ✅ Fixed |
| Missing metrics in markAsFailed/dequeue | ✅ Fixed |
| timeOperation NPE risk | ✅ Fixed |
| Missing newline in application.yml | ✅ Fixed |
| Circuit breaker event metrics | ℹ️ By design (DB trigger) |

---

## Test Coverage

**Recommendation:** Consider adding tests for `ObservabilityService`:
- Unit tests verifying counter caching works correctly
- Integration tests verifying metrics are exported to OTLP endpoint
