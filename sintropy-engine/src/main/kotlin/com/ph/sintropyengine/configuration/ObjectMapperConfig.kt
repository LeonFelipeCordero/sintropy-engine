package com.ph.sintropyengine.configuration

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ph.sintropyengine.utils.DEFAULT_TIME_ZONE
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.text.SimpleDateFormat
import java.util.TimeZone

@ApplicationScoped
class ObjectMapperConfig {

    @Produces
    fun objectMapper() = CustomObjectMapper()
}

class CustomObjectMapper : JsonMapper() {
    init {
        registerModule(JavaTimeModule())
        registerKotlinModule {
            configure(KotlinFeature.NullIsSameAsDefault, true)
        }
        configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        setDateFormat(dateFormat)
        // TODO prepare the system to let the user submit their timezone
//        setTimeZone(TimeZone.getTimeZone(DEFAULT_TIME_ZONE))
    }
}