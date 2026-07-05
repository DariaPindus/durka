package com.durka.backend.telegram.feed

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Capability-token auth for the feed API: a long random token, passed as a query param (works
 * for a bookmarked URL with no JS, no cookies - the constraint that rules out a login-session
 * approach for the legacy Opera Mini client) or an Authorization: Bearer header (for API clients).
 *
 * Every request is logged (valid or not) via FeedAccessLogRepository - this doesn't alert on
 * anything yet, it just captures IP/user-agent/timestamp so a future pass can build "notify me
 * if a new device uses this token" on top without re-deriving this data later.
 */
class FeedAccessFilter(
    private val expectedToken: String,
    private val accessLogRepository: FeedAccessLogRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val providedToken = request.getParameter("token")
            ?: request.getHeader("Authorization")?.removePrefix("Bearer ")

        val valid = providedToken != null && constantTimeEquals(providedToken, expectedToken)

        accessLogRepository.record(
            ipAddress = resolveClientIp(request),
            userAgent = request.getHeader("User-Agent"),
            path = request.requestURI,
            tokenValid = valid,
        )

        if (!valid) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        // Opera Mini's proxy caches aggressively; this is a personal, per-token feed, never safe
        // to cache or share across whoever else happens to hit the same compression proxy.
        response.setHeader("Cache-Control", "no-store")
        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /**
     * No reverse proxy in front yet (direct Docker port mapping) - X-Forwarded-For won't be set
     * until one exists, so this currently resolves to Docker's internal gateway IP. Once a real
     * reverse proxy (Caddy/nginx per the project brief) sits in front, this starts reflecting
     * real client IPs with no code change needed here.
     */
    private fun resolveClientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: request.remoteAddr
}
