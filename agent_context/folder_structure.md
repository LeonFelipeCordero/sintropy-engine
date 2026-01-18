# Folder Structure

## Purpose

This document defines the project layout, package organization, and file conventions for Sintropy Engine.

## Invariants

- Each domain (channel, producer, consumption, iac) follows the same 4-layer pattern
- Generated JOOQ classes are in `src/main/kotlin-gen/` and must not be manually edited
- Database migrations are in `src/main/resources/db/migration/` with Flyway naming
- Test classes mirror the main source structure
- Configuration files use YAML format

## Constraints

- Package naming: `com.ph.sintropyengine.broker.{domain}`
- Migration naming: `V{version}__{description}.sql` (double underscore)
- Test class naming: `{ClassName}Test.kt`
- Response DTO naming: `{Entity}Response.kt`

---

## Project Root

```
sintropy-engine/
├── build.gradle.kts              # Gradle build configuration
├── settings.gradle.kts           # Gradle settings
├── gradlew                       # Gradle wrapper
├── CLAUDE.md                     # AI assistant instructions
├── docker-compose.yaml           # PostgreSQL for development
├── development/
│   └── docker-compose.yaml       # Alternative Docker config
├── agent_context/                # AI context documentation
│   ├── architecture.md
│   ├── api_contracts.md
│   ├── business_logic.md
│   ├── database.md
│   ├── folder_structure.md
│   ├── constraints_and_workarounds.md
│   ├── decision_log.md
│   └── generated/               # Auto-generated summaries
└── src/
    ├── main/
    │   ├── kotlin/              # Application source code
    │   ├── kotlin-gen/          # JOOQ generated classes
    │   └── resources/           # Configuration and migrations
    └── test/
        └── kotlin/              # Test source code
```

---

## Main Source Structure

```
src/main/kotlin/com/ph/sintropyengine/
└── broker/
    ├── channel/                 # Channel domain
    │   ├── api/
    │   │   ├── ChannelApi.kt           # REST: /channels/*
    │   │   ├── ChannelLinkApi.kt       # REST: /channels/links/*
    │   │   └── response/
    │   │       ├── ChannelResponse.kt
    │   │       └── ChannelLinkResponse.kt
    │   ├── model/
    │   │   ├── Channel.kt              # Domain model
    │   │   ├── ChannelLink.kt
    │   │   └── RoutingKeyCircuitState.kt
    │   ├── repository/
    │   │   ├── ChannelRepository.kt    # JOOQ data access
    │   │   └── ChannelLinkRepository.kt
    │   └── service/
    │       ├── ChannelService.kt       # Business logic
    │       └── ChannelLinkService.kt
    │
    ├── producer/                # Producer domain
    │   ├── api/
    │   │   ├── ProducerApi.kt          # REST: /producers/*
    │   │   └── response/
    │   │       └── ProducerResponse.kt
    │   ├── model/
    │   │   └── Producer.kt
    │   ├── repository/
    │   │   └── ProducerRepository.kt
    │   └── service/
    │       └── ProducerService.kt
    │
    ├── consumption/             # Consumption domain
    │   ├── api/
    │   │   ├── QueueApi.kt             # REST: /queues/*
    │   │   ├── DeadLetterQueueApi.kt   # REST: /dead-letter-queue/*
    │   │   ├── CircuitBreakerApi.kt    # REST: /circuit-breakers/*
    │   │   ├── MessageStreamingApi.kt  # WebSocket: /ws/streaming/*
    │   │   ├── MessageRecoveryApi.kt   # Message recovery endpoints
    │   │   └── response/
    │   │       ├── MessageResponse.kt
    │   │       ├── DeadLetterMessageResponse.kt
    │   │       ├── CircuitBreakerResponse.kt
    │   │       └── MessageLogResponse.kt
    │   ├── model/
    │   │   ├── Message.kt
    │   │   ├── MessageLog.kt
    │   │   ├── MessagePreStore.kt      # DTO for message creation
    │   │   ├── DeadLetterMessage.kt
    │   │   ├── ChannelCircuitBreaker.kt
    │   │   ├── MessageStatus.kt        # Enum
    │   │   └── CircuitState.kt         # Enum
    │   ├── repository/
    │   │   ├── MessageRepository.kt
    │   │   ├── DeadLetterQueueRepository.kt
    │   │   └── CircuitBreakerRepository.kt
    │   └── service/
    │       ├── PollingQueue.kt         # Interface
    │       ├── PollingStandardQueue.kt
    │       ├── PollingFifoQueue.kt
    │       ├── CircuitBreakerService.kt
    │       ├── DeadLetterQueueService.kt
    │       ├── ConnectionRouter.kt     # WebSocket routing
    │       └── replication/
    │           ├── PGReplicationConsumer.kt
    │           ├── PGReplicationConsumerImpl.kt
    │           ├── PGReplicationConsumerFaker.kt
    │           ├── PGReplicationConsumerFactory.kt
    │           └── PGReplicationController.kt
    │
    ├── iac/                     # Infrastructure as Code domain
    │   ├── model/
    │   │   └── IaC.kt                  # IaC configuration models
    │   ├── repository/
    │   │   └── IaCRepository.kt
    │   └── service/
    │       └── IaCService.kt
    │
    └── shared/                  # Shared utilities
        ├── configuration/
        │   ├── FeatureFlags.kt
        │   ├── ObjectMapperConfig.kt
        │   └── DatabaseProperties.kt
        └── utils/
            ├── JsonbSerializer.kt      # JOOQ JSONB serialization
            ├── TimeUtils.kt
            └── Patterns.kt
```

---

## Generated JOOQ Classes

```
src/main/kotlin-gen/com/ph/sintropyengine/jooq/generated/
├── DefaultCatalog.java
├── DefaultSchema.java
├── Indexes.java
├── Keys.java
├── Tables.java                  # Main entry point: Tables.CHANNELS, etc.
├── enums/
│   ├── ChannelType.java         # QUEUE, STREAM
│   ├── ConsumptionType.java     # STANDARD, FIFO
│   ├── MessageStatusType.java   # READY, IN_FLIGHT, FAILED
│   └── CircuitState.java        # CLOSED, OPEN
├── tables/
│   ├── Channels.java
│   ├── RoutingKeys.java
│   ├── Queues.java
│   ├── Producers.java
│   ├── Messages.java
│   ├── MessageLog.java
│   ├── ChannelLinks.java
│   ├── DeadLetterQueue.java
│   ├── ChannelCircuitBreakers.java
│   └── IacFiles.java
└── tables/records/
    └── {Table}Record.java       # Record classes for each table
```

---

## Resources

```
src/main/resources/
├── application.yml              # Main Quarkus configuration
├── application-test.yml         # Test profile configuration
└── db/migration/
    ├── V1.0__create-channels-table.sql
    ├── V1.2__create-producers-table.sql
    ├── V1.3__create-messages-table.sql
    ├── V1.4__create-publication.sql
    ├── V1.5__create-channel-links-table.sql
    ├── V1.6__add-message-routing.sql
    ├── V1.7__create-dead-letter-queue.sql
    ├── V1.8__create-iac-table.sql
    └── V1.9__create-channel-circuit-breaker.sql
```

---

## Test Structure

```
src/test/kotlin/com/ph/sintropyengine/
├── IntegrationTestBase.kt       # Base class with helpers
├── PostgresqlDBTestResource.kt  # TestContainers setup
├── TestingUtils.kt
└── broker/
    ├── channel/
    │   ├── api/
    │   │   └── ChannelApiTest.kt
    │   └── service/
    │       ├── ChannelServiceTest.kt
    │       └── ChannelLinkServiceTest.kt
    ├── producer/
    │   ├── api/
    │   │   └── ProducerApiTest.kt
    │   └── service/
    │       └── ProducerServiceTest.kt
    ├── consumption/
    │   ├── api/
    │   │   ├── QueueApiTest.kt
    │   │   └── DeadLetterQueueApiTest.kt
    │   └── service/
    │       ├── PollingQueueTest.kt
    │       └── CircuitBreakerServiceTest.kt
    └── iac/
        └── service/
            └── IaCServiceTest.kt
```

---

## Key File Locations

| What | Where |
|------|-------|
| REST endpoints | `broker/{domain}/api/{Domain}Api.kt` |
| Request DTOs | Bottom of API files (data classes) |
| Response DTOs | `broker/{domain}/api/response/{Entity}Response.kt` |
| Domain models | `broker/{domain}/model/{Entity}.kt` |
| Business logic | `broker/{domain}/service/{Domain}Service.kt` |
| Database access | `broker/{domain}/repository/{Domain}Repository.kt` |
| JOOQ tables | `jooq/generated/Tables.java` |
| Migrations | `resources/db/migration/V{version}__*.sql` |
| Test base class | `IntegrationTestBase.kt` |
| IaC file | `$HOME/.sintropy-engine/init.json` |

---

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| REST Controller | `{Domain}Api.kt` | `ChannelApi.kt` |
| Service | `{Domain}Service.kt` | `ChannelService.kt` |
| Repository | `{Domain}Repository.kt` | `ChannelRepository.kt` |
| Domain Model | `{Entity}.kt` | `Channel.kt` |
| Response DTO | `{Entity}Response.kt` | `ChannelResponse.kt` |
| Test Class | `{ClassName}Test.kt` | `ChannelServiceTest.kt` |
| Migration | `V{major}.{minor}__description.sql` | `V1.0__create-channels-table.sql` |
| Index | `{table}_{columns}_idx` | `channels_name_idx` |
