package com.ph.sintropyengine.broker.consumption.service

import com.ph.sintropyengine.IntegrationTestBase
import com.ph.sintropyengine.broker.channel.model.ConsumptionType
import com.ph.sintropyengine.broker.channel.model.ConsumptionType.FIFO
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@QuarkusTest
class CircuitBreakerServiceTest : IntegrationTestBase() {
    @Inject
    private lateinit var pollingFifoQueue: PollingFifoQueue

    @BeforeEach
    fun setUp() {
        clean()
    }

    @Nested
    inner class GetCircuitState {
        @Test
        fun `should return CLOSED when no circuit exists`() {
            val channel = createFifoQueueChannel()

            val state = circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first())

            assertThat(state).isEqualTo(CircuitState.CLOSED)
        }

        @Test
        fun `should return OPEN when circuit is open`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            val state = circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first())

            assertThat(state).isEqualTo(CircuitState.OPEN)
        }

        @Test
        fun `should fail when channel not found`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.getCircuitState("non-existent", "key") }
                .withMessageContaining("Channel with name non-existent and routing key key not found")
        }

        @Test
        fun `should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.getCircuitState(channel.name, "invalid-key") }
                .withMessageContaining("Channel with name ${channel.name} and routing key invalid-key not found")
        }
    }

    @Nested
    inner class GetCircuitBreaker {
        @Test
        fun `should return circuit breaker with CLOSED state when channel exists`() {
            val channel = createFifoQueueChannel()

            val circuit = circuitBreakerService.getCircuitBreaker(channel.name, channel.routingKeys.first())

            assertThat(circuit).isNotNull
            assertThat(circuit!!.channelId).isEqualTo(channel.channelId)
            assertThat(circuit.routingKey).isEqualTo(channel.routingKeys.first())
            assertThat(circuit.state).isEqualTo(CircuitState.CLOSED)
        }

        @Test
        fun `should return circuit breaker with OPEN state when circuit is open`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            val circuit = circuitBreakerService.getCircuitBreaker(channel.name, channel.routingKeys.first())

            assertThat(circuit).isNotNull
            assertThat(circuit!!.channelId).isEqualTo(channel.channelId)
            assertThat(circuit.routingKey).isEqualTo(channel.routingKeys.first())
            assertThat(circuit.state).isEqualTo(CircuitState.OPEN)
            assertThat(circuit.failedMessageId).isEqualTo(message.messageId)
        }

        @Test
        fun `should fail when channel not found`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.getCircuitBreaker("non-existent", "key") }
                .withMessageContaining("Channel with name non-existent and routing key key not found")
        }

        @Test
        fun `should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.getCircuitBreaker(channel.name, "invalid-key") }
                .withMessageContaining("Channel with name ${channel.name} and routing key invalid-key not found")
        }
    }

    @Nested
    inner class GetAllOpenCircuits {
        @Test
        fun `should return empty list when no open circuits`() {
            createFifoQueueChannel()

            val circuits = circuitBreakerService.getAllOpenCircuits()

            assertThat(circuits).isEmpty()
        }

        @Test
        fun `should return all open circuits`() {
            val (channel1, producer1) = createChannelWithProducer(FIFO)
            val message1 = publishMessage(channel1, producer1)
            pollingFifoQueue.poll(channel1.channelId!!, channel1.routingKeys.first())
            pollingFifoQueue.markAsFailed(message1.messageId)

            val (channel2, producer2) = createChannelWithProducer(FIFO)
            val message2 = publishMessage(channel2, producer2)
            pollingFifoQueue.poll(channel2.channelId!!, channel2.routingKeys.first())
            pollingFifoQueue.markAsFailed(message2.messageId)

            val circuits = circuitBreakerService.getAllOpenCircuits()

            assertThat(circuits).hasSize(2)
            assertThat(circuits.map { it.channelId }).containsExactlyInAnyOrder(
                channel1.channelId,
                channel2.channelId,
            )
        }
    }

    @Nested
    inner class GetCircuitBreakersForChannel {
        @Test
        fun `should return circuits in CLOSED state when channel exists`() {
            val channel = createFifoQueueChannel()

            val circuits = circuitBreakerService.getCircuitBreakersForChannel(channel.name)

            assertThat(circuits).hasSize(1)
            assertThat(circuits.first().channelId).isEqualTo(channel.channelId)
            assertThat(circuits.first().state).isEqualTo(CircuitState.CLOSED)
        }

        @Test
        fun `should fail when channel not found`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.getCircuitBreakersForChannel("non-existent") }
                .withMessageContaining("Channel with name non-existent not found")
        }
    }

    @Nested
    inner class CloseCircuit {
        @Test
        fun `should close open circuit`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.OPEN)

            circuitBreakerService.closeCircuit(channel.name, channel.routingKeys.first())

            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.CLOSED)
        }

        @Test
        fun `should do nothing when circuit already closed`() {
            val channel = createFifoQueueChannel()

            circuitBreakerService.closeCircuit(channel.name, channel.routingKeys.first())

            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.CLOSED)
        }

        @Test
        fun `should fail when channel not found`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.closeCircuit("non-existent", "key") }
                .withMessageContaining("Channel with name non-existent and routing key key not found")
        }

        @Test
        fun `should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.closeCircuit(channel.name, "invalid-key") }
                .withMessageContaining("Channel with name ${channel.name} and routing key invalid-key not found")
        }
    }

    @Nested
    inner class CloseCircuitAndRecover {
        @Test
        fun `should close circuit and recover messages from DLQ`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message1.messageId)

            val dlqMessages =
                dlqRepository.findAllByChannelIdAndRoutingKey(
                    channel.channelId,
                    channel.routingKeys.first(),
                )
            assertThat(dlqMessages).hasSize(2)

            val recoveredCount =
                circuitBreakerService.closeCircuitAndRecover(
                    channel.name,
                    channel.routingKeys.first(),
                )

            assertThat(recoveredCount).isEqualTo(2)
            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.CLOSED)

            val remainingDlq =
                dlqRepository.findAllByChannelIdAndRoutingKey(
                    channel.channelId,
                    channel.routingKeys.first(),
                )
            assertThat(remainingDlq).isEmpty()

            val messagesInQueue = messageRepository.findAll()
            assertThat(messagesInQueue).hasSize(2)
        }

        @Test
        fun `should return 0 when circuit already closed`() {
            val channel = createFifoQueueChannel()

            val recoveredCount =
                circuitBreakerService.closeCircuitAndRecover(
                    channel.name,
                    channel.routingKeys.first(),
                )

            assertThat(recoveredCount).isEqualTo(0)
        }

        @Test
        fun `should fail when channel not found`() {
            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.closeCircuitAndRecover("non-existent", "key") }
                .withMessageContaining("Channel with name non-existent and routing key key not found")
        }

        @Test
        fun `should fail when routing key not in channel`() {
            val channel = createFifoQueueChannel()

            assertThatExceptionOfType(IllegalStateException::class.java)
                .isThrownBy { circuitBreakerService.closeCircuitAndRecover(channel.name, "invalid-key") }
                .withMessageContaining("Channel with name ${channel.name} and routing key invalid-key not found")
        }
    }

    @Nested
    inner class CircuitBreakerBehavior {
        @Test
        fun `should open circuit when FIFO message fails`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.OPEN)
        }

        @Test
        fun `should move existing messages to DLQ when circuit opens`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message1 = publishMessage(channel, producer)
            val message2 = publishMessage(channel, producer)
            val message3 = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message1.messageId)

            val messagesInQueue = messageRepository.findAll()
            assertThat(messagesInQueue).isEmpty()

            val dlqMessages =
                dlqRepository.findAllByChannelIdAndRoutingKey(
                    channel.channelId,
                    channel.routingKeys.first(),
                )
            assertThat(dlqMessages).hasSize(3)
            assertThat(dlqMessages.map { it.messageId }).containsExactlyInAnyOrder(
                message1.messageId,
                message2.messageId,
                message3.messageId,
            )
        }

        @Test
        fun `should route new messages to DLQ when circuit is open`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message1 = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message1.messageId)

            val newMessage = publishMessage(channel, producer)

            val messagesInQueue = messageRepository.findAll()
            assertThat(messagesInQueue).isEmpty()

            val dlqMessages =
                dlqRepository.findAllByChannelIdAndRoutingKey(
                    channel.channelId,
                    channel.routingKeys.first(),
                )
            assertThat(dlqMessages.map { it.messageId }).contains(newMessage.messageId)
        }

        @Test
        fun `should not open circuit for standard queue`() {
            val (channel, producer) = createChannelWithProducer()
            val message = publishMessage(channel, producer)

            val pollingStandardQueue = PollingStandardQueue(messageRepository)
            pollingStandardQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingStandardQueue.markAsFailed(message.messageId)

            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.CLOSED)
        }

        @Test
        fun `should open circuit for stream channel`() {
            val (channel, producer) = createChannelWithProducer(FIFO)
            val message = publishMessage(channel, producer)

            pollingFifoQueue.poll(channel.channelId!!, channel.routingKeys.first())
            pollingFifoQueue.markAsFailed(message.messageId)

            assertThat(circuitBreakerService.getCircuitState(channel.name, channel.routingKeys.first()))
                .isEqualTo(CircuitState.OPEN)
        }
    }
}
