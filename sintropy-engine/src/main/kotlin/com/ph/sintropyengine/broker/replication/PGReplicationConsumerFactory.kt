package com.ph.sintropyengine.broker.replication

import com.ph.sintropyengine.configuration.DatabaseProperties
import com.ph.sintropyengine.configuration.FeatureFlags
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.enterprise.context.ApplicationScoped

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class PGReplicationConsumerFactory(
    private val databaseProperties: DatabaseProperties,
    private val featureFlags: FeatureFlags
) {


    fun getStreamConsumer(): PGReplicationConsumer {
        if (featureFlags.withFullReplication()) {
            logger.debug { "Using full replication consumer" }
            return PGReplicationConsumerImpl(databaseProperties)
        }

        logger.debug { "Using FAKE replication consumer" }
        return PGReplicationConsumerFaker()
    }

}