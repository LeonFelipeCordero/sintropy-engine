package com.ph.syntropyengine.utils

import java.time.OffsetDateTime
import java.time.ZoneId

fun OffsetDateTime.atLocalZone(): OffsetDateTime {
    return this.atZoneSameInstant(ZoneId.of("Europe/Berlin")).toOffsetDateTime()
}
