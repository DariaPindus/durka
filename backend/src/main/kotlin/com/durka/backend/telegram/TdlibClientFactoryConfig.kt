package com.durka.backend.telegram

import it.tdlight.client.SimpleTelegramClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TdlibClientFactoryConfig {

    // Only a single SimpleTelegramClientFactory instance is allowed per JVM process (tdlight-java constraint) -
    // hence one shared Spring-managed bean rather than one per runner.
    //
    // No destroyMethod here deliberately: the factory's own close() blocks waiting for a graceful
    // TDLib client shutdown handshake, which was observed to hang indefinitely (a TDLib-internal
    // auto LoadChats() call fails on Ready and the handshake never completes). Both runners already
    // call the non-blocking client.sendClose() themselves before exiting; letting the OS reclaim
    // the rest on process exit is preferable to a shutdown that never finishes.
    @Bean
    fun telegramClientFactory(): SimpleTelegramClientFactory {
        TdlightNativeInit.ensureInitialized()
        return SimpleTelegramClientFactory()
    }
}
