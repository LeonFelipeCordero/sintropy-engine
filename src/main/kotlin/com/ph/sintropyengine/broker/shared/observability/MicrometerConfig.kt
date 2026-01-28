package com.ph.sintropyengine.broker.shared.observability

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class MicrometerConfig {

    @Produces
    @Singleton
    fun httpServerHistogramFilter(): MeterFilter {
        return object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig {
                if (id.name.startsWith("http.server.requests")) {
                    return DistributionStatisticConfig.builder()
                        .percentilesHistogram(true)
                        .percentiles(0.5, 0.75, 0.90, 0.95, 0.99)
                        .minimumExpectedValue(Duration.ofMillis(1).toNanos().toDouble())
                        .maximumExpectedValue(Duration.ofSeconds(10).toNanos().toDouble())
                        .build()
                        .merge(config)
                }
                return config
            }
        }
    }
}
