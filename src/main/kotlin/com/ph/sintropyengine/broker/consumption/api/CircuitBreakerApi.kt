package com.ph.sintropyengine.broker.consumption.api

import com.ph.sintropyengine.broker.consumption.model.ChannelCircuitBreaker
import com.ph.sintropyengine.broker.consumption.model.CircuitState
import com.ph.sintropyengine.broker.consumption.service.CircuitBreakerService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/circuit-breakers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CircuitBreakerApi(
    private val circuitBreakerService: CircuitBreakerService,
) {
    @GET
    @Path("/open")
    fun listOpenCircuits(): Response {
        val circuits = circuitBreakerService.getAllOpenCircuits()
        return Response.ok(circuits).build()
    }

    @GET
    @Path("/channels/{channelName}")
    fun listCircuitsForChannel(
        @PathParam("channelName") channelName: String,
    ): Response {
        val circuits = circuitBreakerService.getCircuitBreakersForChannel(channelName)
        return Response.ok(circuits).build()
    }

    @GET
    @Path("/channels/{channelName}/routing-keys/{routingKey}")
    fun getCircuitBreaker(
        @PathParam("channelName") channelName: String,
        @PathParam("routingKey") routingKey: String,
    ): Response {
        val circuit = circuitBreakerService.getCircuitBreaker(channelName, routingKey)
        return Response.ok(circuit).build()
    }

    @GET
    @Path("/channels/{channelName}/routing-keys/{routingKey}/state")
    fun getCircuitState(
        @PathParam("channelName") channelName: String,
        @PathParam("routingKey") routingKey: String,
    ): Response {
        val state = circuitBreakerService.getCircuitState(channelName, routingKey)
        return Response.ok(CircuitBreakerStateResponse(state, null)).build()
    }

    @POST
    @Path("/channels/{channelName}/routing-keys/{routingKey}/close")
    fun closeCircuit(
        @PathParam("channelName") channelName: String,
        @PathParam("routingKey") routingKey: String,
        @QueryParam("recover") recover: Boolean?,
    ): Response =
        if (recover == true) {
            val recoveredCount = circuitBreakerService.closeCircuitAndRecover(channelName, routingKey)
            Response.ok(CloseCircuitResponse(success = true, recoveredCount = recoveredCount)).build()
        } else {
            circuitBreakerService.closeCircuit(channelName, routingKey)
            Response.ok(CloseCircuitResponse(success = true, recoveredCount = 0)).build()
        }
}

data class CircuitBreakerStateResponse(
    val state: CircuitState,
    val circuitBreaker: ChannelCircuitBreaker?,
)

data class CloseCircuitResponse(
    val success: Boolean,
    val recoveredCount: Int,
)
