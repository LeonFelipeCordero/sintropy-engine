package com.ph.sintropyengine

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.DockerImageName
import java.io.File

private val customerImage: ImageFromDockerfile =
    ImageFromDockerfile("development-postgres")
        .withDockerfile(File("development/postgres17-wal2json/Dockerfile").toPath())

private val container: PostgreSQLContainer<*> =
    PostgreSQLContainer(
        DockerImageName
            .parse(customerImage.get())
            .asCompatibleSubstituteFor("postgres"),
    )
        // TODO the image is not taking default configurations from docker image
        // try in the future to push to a container registry and pull from there
        .withCreateContainerCmdModifier {
            it.withCmd(
                *it.cmd,
                "-c",
                "wal_level=logical",
                "-c",
                "max_wal_senders=15",
                "-c",
                "max_replication_slots=15",
                "-c",
                "max_logical_replication_workers=15",
                "-c",
                "max_worker_processes=16",
                "-c",
                "hba_file=/etc/postgresql/pg_hba.conf",
            )
        }.withReuse(true)
        .withUsername("postgres")
        .withPassword("postgres")
        .withDatabaseName("postgres")

class PostgresqlDBTestResource : QuarkusTestResourceLifecycleManager {
    override fun start(): Map<String?, String?> {
        container.start()

        return mutableMapOf(
            "quarkus.datasource.jdbc.url" to container.jdbcUrl,
            "quarkus.datasource.username" to container.username,
            "quarkus.datasource.password" to container.password,
        )
    }

    override fun stop() {
        container.stop()
    }
}
