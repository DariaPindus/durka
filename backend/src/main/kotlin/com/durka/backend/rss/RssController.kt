package com.durka.backend.rss

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class RssEntryDto(
    val title: String?,
    val link: String?,
    val publishedAt: String,
)

data class RssSourceGroupDto(
    val feedUrl: String,
    val feedTitle: String?,
    val entries: List<RssEntryDto>,
)

/** Token auth on requests under /api is enforced by FeedAccessFilter (see FeedSecurityConfig). */
@RestController
@RequestMapping("/api/rss")
class RssController(private val repository: RssItemRepository) {

    @GetMapping("/recent")
    fun recent(@RequestParam(defaultValue = "25") limit: Int): List<RssSourceGroupDto> =
        repository.findRecent(limit)
            .groupBy { it.feedUrl to it.feedTitle }
            .map { (source, items) ->
                RssSourceGroupDto(
                    feedUrl = source.first,
                    feedTitle = source.second,
                    entries = items
                        .sortedByDescending { it.publishedAt }
                        .map { RssEntryDto(title = it.title, link = it.link, publishedAt = it.publishedAt.toString()) },
                )
            }
            .sortedByDescending { it.entries.firstOrNull()?.publishedAt }
}
