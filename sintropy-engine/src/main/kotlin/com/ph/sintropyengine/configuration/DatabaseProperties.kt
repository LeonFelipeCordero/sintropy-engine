package com.ph.sintropyengine.configuration

import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@ConfigMapping(prefix = "quarkus.datasource")
interface DatabaseProperties {
    fun jdbc(): JDBCProperties
    fun username(): String
    fun password(): String

    fun jdbcUrl(): String {
        return jdbc().url()
    }
}

interface JDBCProperties {
    fun url(): String
}