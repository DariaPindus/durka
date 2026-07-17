package com.durka.backend.telegram.feed

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// FeedSecurityProperties is picked up by BackendApplication's @ConfigurationPropertiesScan already -
// no @EnableConfigurationProperties here, that would double-register the same bean.
@Configuration
class FeedSecurityConfig(
    private val properties: FeedSecurityProperties,
    private val accessLogRepository: FeedAccessLogRepository,
) {

    @Bean
    fun feedAccessFilter(): FilterRegistrationBean<FeedAccessFilter> {
        val registration = FilterRegistrationBean(FeedAccessFilter(properties.token, accessLogRepository))
        // One explicit pattern per module rather than a blanket "/api/*" - that would also catch
        // /api/ping, which is deliberately unauthenticated (a plain connectivity check, see
        // PingController/README). Add a new entry here whenever a new module's API lands.
        registration.addUrlPatterns("/api/messages/*", "/api/rss/*")
        return registration
    }
}
