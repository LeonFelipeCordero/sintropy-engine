package com.ph.syntropyengine.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "syen.feature-flags")
class FeatureFlags(
    var withFullReplication: Boolean = false,
)