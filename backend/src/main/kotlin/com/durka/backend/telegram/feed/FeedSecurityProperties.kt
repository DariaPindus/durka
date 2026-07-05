package com.durka.backend.telegram.feed

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("feed")
data class FeedSecurityProperties(
    /** Long random capability token - read-only scope, meant to be rotated periodically. */
    val token: String,
)
