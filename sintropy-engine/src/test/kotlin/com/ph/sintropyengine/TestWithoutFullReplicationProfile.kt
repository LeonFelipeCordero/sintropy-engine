package com.ph.sintropyengine

import io.quarkus.test.junit.QuarkusTestProfile

class TestWithoutFullReplicationProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): MutableMap<String, String> {
        return mutableMapOf(
            "syen.feature-flags.with-full-replication" to "false"
        )
    }
}