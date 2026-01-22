package com.ph.sintropyengine.broker.iac.model

import java.time.OffsetDateTime
import java.util.UUID

data class IaCFile(
    val fileId: Long,
    val fileUuid: UUID,
    val fileName: String,
    val hash: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
