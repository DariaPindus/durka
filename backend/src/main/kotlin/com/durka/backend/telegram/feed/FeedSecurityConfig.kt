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
        registration.addUrlPatterns("/api/messages/*")
        return registration
    }
}
