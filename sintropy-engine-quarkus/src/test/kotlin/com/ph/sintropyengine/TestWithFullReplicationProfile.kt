package com.ph.sintropyengine

import io.quarkus.test.junit.QuarkusTestProfile

class TestWithFullReplicationProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): MutableMap<String, String> {
        return mutableMapOf(
            "syen.feature-flags.with-full-replication" to "true"
        )
    }
}