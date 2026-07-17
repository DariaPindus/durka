package com.durka.backend.rss

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RssFeedSummaryDto(
    val feedUrl: String,
    val feedTitle: String?,
    val itemCount: Int,
    val lastPublishedAt: String,
)

data class RssEntrySummaryDto(
    val id: Long,
    val title: String?,
    val publishedAt: String,
)

data class RssEntryDetailDto(
    val id: Long,
    val feedUrl: String,
    val feedTitle: String?,
    val title: String?,
    val link: String?,
    val publishedAt: String,
    val description: String?,
)

/** Token auth on requests under /api is enforced by FeedAccessFilter (see FeedSecurityConfig). */
@RestController
@RequestMapping("/api/rss")
class RssController(private val repository: RssItemRepository) {

    @GetMapping("/feeds")
    fun feeds(): List<RssFeedSummaryDto> =
        repository.findFeeds().map {
            RssFeedSummaryDto(
                feedUrl = it.feedUrl,
                feedTitle = it.feedTitle,
                itemCount = it.itemCount,
                lastPublishedAt = it.lastPublishedAt.toString(),
            )
        }

    @GetMapping("/entries")
    fun entries(@RequestParam feedUrl: String, @RequestParam(defaultValue = "10") limit: Int): List<RssEntrySummaryDto> =
        repository.findByFeed(feedUrl, limit).map {
            RssEntrySummaryDto(id = it.id, title = it.title, publishedAt = it.publishedAt.toString())
        }

    @GetMapping("/entries/{id}")
    fun entry(@PathVariable id: Long): RssEntryDetailDto {
        val row = repository.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "RSS entry not found")
        return RssEntryDetailDto(
            id = row.id,
            feedUrl = row.feedUrl,
            feedTitle = row.feedTitle,
            title = row.title,
            link = row.link,
            publishedAt = row.publishedAt.toString(),
            description = row.description,
        )
    }
}
