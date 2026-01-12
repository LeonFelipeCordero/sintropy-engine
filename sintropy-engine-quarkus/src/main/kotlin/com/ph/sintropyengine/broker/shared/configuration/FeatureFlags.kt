package com.ph.sintropyengine.broker.shared.configuration

import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@ConfigMapping(prefix = "syen.feature-flags")
interface FeatureFlags {
    fun withFullReplication(): Boolean
}
