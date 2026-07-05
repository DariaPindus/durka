package com.durka.backend.telegram

import it.tdlight.client.SimpleTelegramClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TdlibClientFactoryConfig {

    // Only a single SimpleTelegramClientFactory instance is allowed per JVM process (tdlight-java constraint) -
    // hence one shared Spring-managed bean rather than one per runner.
    @Bean(destroyMethod = "close")
    fun telegramClientFactory(): SimpleTelegramClientFactory {
        TdlightNativeInit.ensureInitialized()
        return SimpleTelegramClientFactory()
    }
}
