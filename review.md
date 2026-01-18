# Code Review: API Response Refactoring & Agent Context Files

## Summary
This PR refactors API endpoints to return response DTOs instead of domain models (hiding internal IDs), changes API paths from UUID-based to name-based lookups, and adds agent context documentation files.

---

### 3. N+1 Query Problem in Multiple Endpoints
**Files:** `DeadLetterQueueApi.kt`, `MessageRecoveryService.kt`, `CircuitBreakerApi.kt`, `QueueApi.kt`, `PGReplicationController.kt`

```kotlin
messages.map { message ->
    val producer = producerService.findById(message.producerId)
        ?: throw IllegalStateException("Producer not found")
    message.toResponse(channelName, producer.name)
}
```

**Problem:** For each message, a separate database query is made to fetch the producer. This can be O(n) queries.

**Suggestion:** Create batch lookup methods:
```kotlin
fun findByIds(ids: Set<UUID>): Map<UUID, Producer>
```
Then use:
```kotlin
val producerIds = messages.map { it.producerId }.toSet()
val producersById = producerService.findByIds(producerIds)
messages.map { msg ->
    val producer = producersById[msg.producerId]
        ?: throw IllegalStateException("Producer ${msg.producerId} not found")
    msg.toResponse(channelName, producer.name)
}
```

---

## 🟡 Medium Issues

### 5. Inconsistent Error Handling Pattern
**Files:** Various API files

Some endpoints use:
```kotlin
?: throw IllegalStateException("Channel not found")
```

Others use:
```kotlin
?: return Response.status(Response.Status.NOT_FOUND).build()
```

**Suggestion:** Standardize on one approach. Prefer returning proper HTTP status codes from API layer rather than throwing generic exceptions.

---

### 6. Duplicate Response Mapping Logic
**File:** `MessageRecoveryService.kt:59-68, 81-90, 118-127, 138-147`

The same mapping logic appears 4 times:
```kotlin
val responses = batchMessages.map { msg ->
    val producer = producerService.findById(msg.producerId)
        ?: throw IllegalStateException("Producer not found")
    msg.toResponse(channelName, producer.name)
}
```

**Suggestion:** Extract to a private helper method or create a dedicated mapper service.

---

### 8. Missing Validation on Delete Endpoints
**Files:** `ChannelApi.kt:38-42`, `ProducerApi.kt:54-58`

```kotlin
@DELETE
@Path("/{name}")
fun deleteChannel(@PathParam("name") name: String): Response {
    channelService.deleteByName(name)
    return Response.noContent().build()
}
```

**Problem:** Returns 204 even if the resource doesn't exist. This is acceptable REST practice but inconsistent with GET returning 404.

**Suggestion:** Consider returning 404 if resource doesn't exist, or document this is intentional idempotent behavior.

---

## 🟢 Minor Issues / Code Style

### 9. Redundant `return` in `CircuitBreakerApi.kt`
**File:** `CircuitBreakerApi.kt:55-58`

```kotlin
?.let {
    Response.ok(it.toResponse(channelName)).build()
} ?: return Response.noContent().build()
```

**Problem:** The `return` before `Response.noContent()` is redundant since it's already the last expression.

**Suggestion:**
```kotlin
} ?: Response.noContent().build()
```

---

### 12. Test File Assertions Pattern
**File:** `ProducerApiTest.kt:72-73`

```kotlin
.body("name", equalTo(producer.name))
.body("name", equalTo(producer.name))  // Duplicate assertion
```

**Problem:** Same assertion appears twice.

**Suggestion:** Remove the duplicate line.
