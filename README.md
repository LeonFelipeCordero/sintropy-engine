# Sintropy Engine

Sintropy Engine is a message broker built with Quarkus and Kotlin. It provides a reliable and scalable platform for
asynchronous communication between services.

## Features

- **Two channel types:**
  - **Queue** — supports both STANDARD (competing consumers) and FIFO (ordered, exactly-once) consumption modes
  - **Stream** — real-time message delivery over WebSocket connections
- **Channel linking** — route messages between channels automatically, enabling fan-out and pipeline patterns
- **Dead Letter Queue (DLQ)** — failed messages are automatically moved to a DLQ for inspection and reprocessing
- **Circuit breaker** — for FIFO channels, a failed message opens the circuit and routes remaining messages to the DLQ, preventing out-of-order processing
- **PostgreSQL-native** — uses PostgreSQL logical replication (`wal2json`) to stream message inserts to WebSocket consumers in real-time, and advisory locks to guarantee single-consumer message processing
- **Infrastructure as Code** — define channels, routing keys, and producers declaratively through IaC file definitions

## Prerequisites

To build and run this project, you will need the following tools:

- **Java 21:** The project is built with Java 21.
- **Kotlin:** The primary language for this project is Kotlin.
- **Gradle:** Gradle is used for building the project and managing dependencies.
- **Docker:** Docker is required for running the local development environment.

## Local Development Setup

The local development environment uses Docker to run a PostgreSQL database with the `wal2json` plugin enabled.

To start the local database, run the following command:

```shell script
./development/build-database.sh
./development/build-app.sh
```

## Build schema and jooq code

```shell script
./gradlew clean build -x test -Dapi.version=1.44
./gradlew flywayMigrate
./gradlew jooqCodegen
```

## Running the application in dev mode

You can run your application in dev mode, which enables live coding, using the following command:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Running Tests

To run the test suite, use the following command:

```shell script
./gradlew test -Dapi.version=1.44
```

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build -Dapi.version=1.44
```

## Contributing

Contributions are welcome! If you would like to contribute to the project, please follow these steps:

1. Create a new branch for your feature or bug fix.
2. Make your changes and commit them with a descriptive message.
3. Push your changes to your branch.
4. Create a pull request to the main repository.
