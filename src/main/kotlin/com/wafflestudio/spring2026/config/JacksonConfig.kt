package com.wafflestudio.spring2026.config

import org.openapitools.jackson.nullable.JsonNullableJackson3Module
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

    @Bean
    fun jsonNullableJackson3Module() =
        JsonNullableJackson3Module()
}