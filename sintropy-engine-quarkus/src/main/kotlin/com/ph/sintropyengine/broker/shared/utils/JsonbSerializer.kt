package com.ph.sintropyengine.broker.shared.utils

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.jooq.JSONB

class JsonbSerializer : JsonSerializer<JSONB>() {
    override fun serialize(
        value: JSONB,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        gen.writeString(value.data())
    }
}

class JsonbDeserializer : JsonDeserializer<JSONB>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): JSONB = JSONB.valueOf(p.text)
}
