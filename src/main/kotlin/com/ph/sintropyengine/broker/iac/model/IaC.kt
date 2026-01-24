package com.ph.sintropyengine.broker.iac.model

data class IaC(
    val channels: List<ChannelIaC>,
    val producers: List<ProducerIaC>,
    val channelLinks: List<ChannelLinkIaC>,
)

data class ChannelIaC(
    val name: String,
    val channelType: String,
    val consumptionType: String?,
    val routingKeys: List<String>,
)

data class ProducerIaC(
    val name: String,
)

data class ChannelLinkIaC(
    val sourceChannelName: String,
    val targetChannelName: String,
    val sourceRoutingKey: String,
    val targetRoutingKey: String,
)
