package com.ph.sintropyengine

import io.quarkus.test.junit.QuarkusTestProfile

class TestWithoutFullReplicationProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> {
        return mapOf(
            "syen.feature-flags.with-full-replication" to "false"
        )
    }
}