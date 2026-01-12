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
            sourceChannelId = record.sourceChannelId,
            targetChannelId = record.targetChannelId,
            sourceRoutingKey = record.sourceRoutingKey,
            targetRoutingKey = record.targetRoutingKey,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            enabled = record.enabled,
        )
    }

    fun findById(channelLinkId: UUID) =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.CHANNEL_LINK_ID.eq(channelLinkId))
            .fetchOneInto(ChannelLink::class.java)

    fun findBySourceChannelId(sourceChannelId: UUID): List<ChannelLink> =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.SOURCE_CHANNEL_ID.eq(sourceChannelId))
            .fetchInto(ChannelLink::class.java)

    fun findByTargetChannelId(targetChannelId: UUID): List<ChannelLink> =
        context
            .selectFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.TARGET_CHANNEL_ID.eq(targetChannelId))
            .fetchInto(ChannelLink::class.java)

    fun delete(channelLinkId: UUID) {
        context
            .deleteFrom(CHANNEL_LINKS)
            .where(CHANNEL_LINKS.CHANNEL_LINK_ID.eq(channelLinkId))
            .execute()
    }

    fun setEnabled(
        channelLinkId: UUID,
        enabled: Boolean,
    ) {
        context
            .update(CHANNEL_LINKS)
            .set(CHANNEL_LINKS.ENABLED, enabled)
            .set(CHANNEL_LINKS.UPDATED_AT, OffsetDateTime.now())
            .where(CHANNEL_LINKS.CHANNEL_LINK_ID.eq(channelLinkId))
            .execute()
    }

    fun deleteAll() {
        context.deleteFrom(CHANNEL_LINKS).execute()
    }
}
