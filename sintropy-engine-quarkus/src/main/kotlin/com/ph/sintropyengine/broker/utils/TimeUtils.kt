package com.ph.sintropyengine.utils

import java.time.OffsetDateTime
import java.time.ZoneId

const val DEFAULT_TIME_ZONE = "Europe/Berlin"

// TODO let the user choose the default timezone
fun OffsetDateTime.atLocalZone(): OffsetDateTime =
    this.atZoneSameInstant(ZoneId.of(DEFAULT_TIME_ZONE)).toOffsetDateTime()
