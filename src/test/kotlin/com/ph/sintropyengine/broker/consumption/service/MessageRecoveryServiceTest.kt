package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.IntegrationTestBase
import io.quarkus.test.junit.QuarkusTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class MessageRecoveryServiceTest : IntegrationTestBase() {
    @BeforeEach
    fun setUp() {
        clean()
    }

    @Test
    fun `should retrigger message and return message when it exists`() {
        val message = publishMessage()

        val retriggeredMessage = messageRecoveryService.retriggerMessage(message.messageId)

        assertThat(retriggeredMessage).isNotNull
        assertThat(retriggeredMessage.messageId).isEqualTo(message.messageId)
        assertThat(retriggeredMessage.channelId).isEqualTo(message.channelId)
        assertThat(retriggeredMessage.producerId).isEqualTo(message.producerId)
        assertThat(retriggeredMessage.routingKey).isEqualTo(message.routingKey)
    }

    @Test
    fun `should fail to retrigger message when message does not exist`() {
        val nonExistentId = UUID.randomUUID()

        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { messageRecoveryService.retriggerMessage(nonExistentId) }
            .withMessage("Message with id $nonExistentId was not found")
    }
}
