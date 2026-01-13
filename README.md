# Sintropy Engine

Sintropy Engine is a message broker built with Quarkus and Kotlin. It provides a reliable and scalable platform for asynchronous communication between services.

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
docker-compose -f development/docker-compose.yaml up -d
```

This will start a PostgreSQL database on port 5432. The default credentials are:

- **Username:** `postgres`
- **Password:** `postgres`

## Running the application in dev mode

You can run your application in dev mode, which enables live coding, using the following command:

```shell script
./gradlew quarkusDev -Dapi.version=1.44
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
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/sintropy-engine-1.0-SNAPSHOT-runner`

## Contributing

Contributions are welcome! If you would like to contribute to the project, please follow these steps:

1.  Fork the repository.
2.  Create a new branch for your feature or bug fix.
3.  Make your changes and commit them with a descriptive message.
4.  Push your changes to your fork.
5.  Create a pull request to the main repository.