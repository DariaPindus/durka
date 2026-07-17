package com.durka.backend.rss

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Polls every feed in RssProperties on a fixed schedule and stores new entries - RSS has no live
 * push mechanism (unlike Telegram's UpdateNewMessage), so periodic polling is the only option.
 * Gated to the same "!auth-cli" profile as the Telegram side's background work, so the one-shot
 * login process doesn't also spin up unrelated scheduled jobs.
 */
@Component
@Profile("!auth-cli")
class RssFeedPoller(
    private val properties: RssProperties,
    private val repository: RssItemRepository,
) {

    private val log = LoggerFactory.getLogger(RssFeedPoller::class.java)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @Scheduled(initialDelay = 0, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun pollAll() {
        properties.urls().forEach { feedUrl -> pollFeed(feedUrl) }
    }

    private fun pollFeed(feedUrl: String) {
        try {
            // Fetched ourselves via HttpClient (timeout + a real User-Agent - some feeds reject
            // generic/absent ones) rather than XmlReader's own URL constructor, which is
            // deprecated in favor of handing it an already-open stream.
            val request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "durka-rss-poller/1.0")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

            val feed = response.body().use { stream -> XmlReader(stream).use { reader -> SyndFeedInput().build(reader) } }
            val feedTitle = feed.title

            feed.entries.forEach { entry ->
                // guid (entry.uri) is the spec's own dedup key; link is the practical fallback
                // for feeds that omit it. Skip an entry with neither - nothing to key it on.
                val externalId = entry.uri ?: entry.link ?: return@forEach

                // Prefer content:encoded (entry.contents) over <description> - some feeds put a
                // short summary in description and the real full body in content:encoded. Falls
                // back to description for the (more common) feeds that only have the latter.
                val description = entry.contents.firstOrNull()?.value ?: entry.description?.value

                repository.insert(
                    NewRssItem(
                        feedUrl = feedUrl,
                        feedTitle = feedTitle,
                        externalId = externalId,
                        title = entry.title,
                        link = entry.link,
                        publishedAt = entry.publishedDate?.toInstant() ?: Instant.now(),
                        description = description,
                    )
                )
            }
        } catch (ex: Exception) {
            // One bad/unreachable feed shouldn't stop the others from polling, and there's no
            // request to fail here anyway - just log it and try again next cycle.
            log.warn("Failed to poll RSS feed {}", feedUrl, ex)
        }
    }
}
