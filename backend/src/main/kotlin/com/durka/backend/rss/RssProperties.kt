package com.durka.backend.rss

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("rss")
data class RssProperties(
    /** Space-separated list of RSS/Atom feed URLs to poll - kept as a single string (not a
     * List) since a plain space-separated env var is simpler to set than the comma/index-based
     * syntax Spring Boot needs to bind an env var to a List property. */
    val feedUrls: String = "",
) {
    fun urls(): List<String> = feedUrls.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
}
