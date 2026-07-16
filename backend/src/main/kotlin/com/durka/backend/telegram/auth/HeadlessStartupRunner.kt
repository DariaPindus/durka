package com.durka.backend.telegram.auth

import com.durka.backend.telegram.TdlibSettingsFactory
import com.durka.backend.telegram.TelegramClientHolder
import com.durka.backend.telegram.feed.FeedItemIngestor
import com.durka.backend.telegram.repository.TelegramSessionStatusRepository
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Normal headless startup: docker compose up -d.
 *
 * Reuses the session persisted by AuthCliRunner in the TDLib data volume - never prompts. If no
 * session has been established yet, or the persisted one turns out to be invalid, this fails fast
 * (FailFastClientInteraction) rather than hanging on a stdin read with no TTY attached.
 */
@Component
@Profile("!auth-cli")
class HeadlessStartupRunner(
    private val clientFactory: SimpleTelegramClientFactory,
    private val settingsFactory: TdlibSettingsFactory,
    private val sessionRepository: TelegramSessionStatusRepository,
    private val feedItemIngestor: FeedItemIngestor,
    private val telegramClientHolder: TelegramClientHolder,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(HeadlessStartupRunner::class.java)

    override fun run(args: ApplicationArguments) {
        val existing = sessionRepository.findSession()
        if (existing == null) {
            log.error(
                "No Telegram session found. Run the auth-cli profile first: " +
                    "docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app"
            )
            exitProcess(1)
        }

        val builder = clientFactory.builder(settingsFactory.create())
        builder.setClientInteraction(FailFastClientInteraction())

        val shutdownLatch = CountDownLatch(1)

        // Read by the UpdateNewMessage handler below, only once an actual update arrives - which
        // happens after builder.build() (a few lines down) has already assigned it. Kotlin doesn't
        // allow `lateinit` on local variables, hence the nullable-plus-!! forward reference.
        var clientRef: SimpleTelegramClient? = null

        builder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            when (update.authorizationState) {
                is TdApi.AuthorizationStateReady ->
                    log.info("Telegram session restored, authorization ready")
                is TdApi.AuthorizationStateClosed -> {
                    log.error("Telegram client closed unexpectedly - session may be invalid. Run auth-cli again.")
                    shutdownLatch.countDown()
                }
                else ->
                    log.info("Authorization state: {}", update.authorizationState.javaClass.simpleName)
            }
        }

        builder.addUpdateHandler(TdApi.UpdateNewMessage::class.java) { update ->
            feedItemIngestor.handle(clientRef!!, update)
        }

        val client = builder.build(AuthenticationSupplier.user(existing.phoneNumber))
        clientRef = client
        telegramClientHolder.set(client)

        try {
            val me = client.getMeAsync().get(30, TimeUnit.SECONDS)
            if (me == null) {
                // Same stale-session case as AuthCliRunner: Ready locally, but the first real API
                // call reveals the session was actually ended remotely. Clear it here too so the
                // next auth-cli run doesn't also need a manual volume wipe.
                log.error(
                    "Existing session is invalid (likely logged out remotely) - clearing stale " +
                        "local session data. Run the auth-cli profile again to log in fresh."
                )
                client.sendClose()
                settingsFactory.clearSessionData()
                exitProcess(1)
            }
            log.info("Confirmed session for {} {} (id={})", me.firstName, me.lastName, me.id)
            sessionRepository.upsertReady(
                phoneNumber = me.phoneNumber,
                telegramUserId = me.id,
                verifiedAt = Instant.now(),
            )
        } catch (ex: Exception) {
            log.error("Failed to confirm existing Telegram session. Run the auth-cli profile again.", ex)
            // sendClose() is fire-and-forget, unlike closeAndWait() - a hung/incomplete TDLib
            // close handshake here must never block process exit (observed during implementation:
            // a stuck closeAndWait() left the container running indefinitely instead of failing).
            client.sendClose()
            exitProcess(1)
        }

        // Spring Boot's own SIGTERM shutdown hook closes the context (and this bean's factory);
        // this hook just releases the latch blocking this runner so the JVM doesn't exit early
        // once startup work is done - see plan notes on the "no web server -> exits immediately" gotcha.
        Runtime.getRuntime().addShutdownHook(
            Thread {
                client.sendClose()
                shutdownLatch.countDown()
            }
        )

        shutdownLatch.await()
    }
}
