package com.durka.backend.telegram

import it.tdlight.client.SimpleTelegramClient
import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Holds a reference to the live TDLib client once HeadlessStartupRunner has built and confirmed
 * it, so request-handling beans (e.g. a controller sending a reply) can use it too - the client
 * itself isn't a Spring bean (it's built imperatively as part of the startup/auth flow, not via
 * a @Bean method), so this is the seam between that flow and everything else that needs it.
 */
@Component
class TelegramClientHolder {

    @Volatile
    private var client: SimpleTelegramClient? = null

    fun set(client: SimpleTelegramClient) {
        this.client = client
    }

    fun requireClient(): SimpleTelegramClient =
        client ?: throw IllegalStateException("Telegram client is not ready yet")

    fun sendMessage(chatId: Long, text: String): CompletableFuture<TdApi.Message> {
        val current = client
            ?: return CompletableFuture.failedFuture(IllegalStateException("Telegram client is not ready yet"))

        val request = TdApi.SendMessage()
        request.chatId = chatId
        val content = TdApi.InputMessageText()
        content.text = TdApi.FormattedText(text, emptyArray())
        request.inputMessageContent = content

        return current.send(request)
    }
}
