package com.ph.sintropyengine.broker.channel.repository

import com.ph.sintropyengine.broker.channel.model.ChannelLink
import com.ph.sintropyengine.jooq.generated.Tables.CHANNEL_LINKS
import jakarta.enterprise.context.ApplicationScoped
import org.jooq.DSLContext
import java.time.OffsetDateTime
import java.util.UUID

@ApplicationScoped
class ChannelLinkRepository(
    private val context: DSLContext,
) {
    fun save(channelLink: ChannelLink): ChannelLink {
        val record =
            context
                .insertInto(
                    CHANNEL_LINKS,
                    CHANNEL_LINKS.SOURCE_CHANNEL_ID,
                    CHANNEL_LINKS.TARGET_CHANNEL_ID,
                    CHANNEL_LINKS.SOURCE_ROUTING_KEY,
                    CHANNEL_LINKS.TARGET_ROUTING_KEY,
                    CHANNEL_LINKS.ENABLED,
                ).values(
                    channelLink.sourceChannelId,
                    channelLink.targetChannelId,
                    channelLink.sourceRoutingKey,
                    channelLink.targetRoutingKey,
                    channelLink.enabled,
                ).returning()
                .fetchOne()

        return ChannelLink(
            channelLinkId = record!!.channelLinkId,
            channelLinkUuid = record.channelLinkUuid,
            sourceChannelId = record.sourceChannelId,
            targetChannelId = record.targetChannelId,
            sourceRoutingKey = record.sourceRoutingKey,
            targetRoutingKey = record.targetRoutingKey,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            enabled = record.enabled,
        )
    }

    fun findById(channelLinkId: Long) =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.CHANNEL_LINK_ID.eq(channelLinkId))
            .fetchOneInto(ChannelLink::class.java)

    fun findByUUID(channelLinkUUID: UUID) =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.CHANNEL_LINK_UUID.eq(channelLinkUUID))
            .fetchOneInto(ChannelLink::class.java)

    fun findBySourceChannelId(sourceChannelId: Long): List<ChannelLink> =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.SOURCE_CHANNEL_ID.eq(sourceChannelId))
            .fetchInto(ChannelLink::class.java)

    fun findByTargetChannelId(targetChannelId: Long): List<ChannelLink> =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.TARGET_CHANNEL_ID.eq(targetChannelId))
            .fetchInto(ChannelLink::class.java)

    fun delete(channelLinkId: Long) {
        context
            .deleteFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.CHANNEL_LINK_ID.eq(channelLinkId))
            .execute()
    }

    fun setEnabled(
        channelLinkId: Long,
        enabled: Boolean,
    ) {
        context
            .update(CHANNEL_LINKS)
            .set(CHANNEL_LINKS.ENABLED, enabled)
            .set(CHANNEL_LINKS.UPDATED_AT, OffsetDateTime.now())
            .where(CHANNEL_LINKS.CHANNEL_LINK_ID.eq(channelLinkId))
            .execute()
    }

    fun findAll(): List<ChannelLink> =
        context
            .selectFrom(CHANNEL_LINKS)
            .fetchInto(ChannelLink::class.java)

    fun deleteAll() {
        context.deleteFrom(CHANNEL_LINKS).execute()
    }
}
