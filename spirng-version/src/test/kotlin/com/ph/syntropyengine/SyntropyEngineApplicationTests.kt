package com.ph.syntropyengine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

class SyntropyEngineApplicationTests : IntegrationTestBase() {

    @Test
    fun contextLoads() {
        assertThat(true).isTrue
    }
}
