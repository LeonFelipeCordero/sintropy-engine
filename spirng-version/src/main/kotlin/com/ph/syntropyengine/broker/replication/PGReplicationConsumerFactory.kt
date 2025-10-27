package com.ph.syntropyengine.broker.replication

import com.ph.syntropyengine.configuration.DatabaseProperties
import com.ph.syntropyengine.configuration.FeatureFlags
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class PGReplicationConsumerFactory(
    private val databaseProperties: DatabaseProperties,
    private val featureFlags: FeatureFlags,
) {

    fun getStreamConsumer(): PGReplicationConsumer {
        if (featureFlags.withFullReplication) {
            logger.debug { "Using full replication consumer" }
            return PGReplicationConsumerImpl(databaseProperties)
        }

        logger.debug { "Using FAKE replication consumer" }
        return PGReplicationConsumerFaker()
    }

}