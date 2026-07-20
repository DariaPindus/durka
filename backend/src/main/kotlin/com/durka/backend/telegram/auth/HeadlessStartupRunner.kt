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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Normal headless startup: docker compose up -d.
 *
 * Reuses the session persisted by AuthCliRunner in the TDLib data volume - never prompts. Telegram
 * is treated as an optional module, not a hard startup dependency: if there's no session yet, or
 * the persisted one turns out to be invalid, this logs a clear error and returns *without*
 * Telegram rather than killing the process - Tomcat and every other module (RSS, notes, ...) keep
 * running regardless of Telegram's state. Anything that actually needs the Telegram client
 * (SendersController) gets a clean 503 via TelegramClientHolder.requireClient() instead of the
 * whole backend being down.
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
            log.warn(
                "No Telegram session found - continuing without Telegram (RSS/notes are unaffected). " +
                    "Run the auth-cli profile to enable it: " +
                    "docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app"
            )
            return
        }

        val builder = clientFactory.builder(settingsFactory.create())
        builder.setClientInteraction(FailFastClientInteraction())

        val shutdownLatch = CountDownLatch(1)

        // Read by the UpdateNewMessage handler below, only once an actual update arrives - which
        // happens after builder.build() (a few lines down) has already assigned it. Kotlin doesn't
        // allow `lateinit` on local variables, hence the nullable-plus-!! forward reference.
        var clientRef: SimpleTelegramClient? = null

        // Set when the update handler below has already logged and cleared the client for
        // AuthorizationStateLoggingOut - lets the catch block downstream recognize "already
        // handled" instead of logging a second, more confusing message about the same event.
        val loggedOutHandled = AtomicBoolean(false)

        builder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            when (update.authorizationState) {
                is TdApi.AuthorizationStateReady ->
                    log.info("Telegram session restored, authorization ready")
                is TdApi.AuthorizationStateLoggingOut -> {
                    // Confirmed by observation: once this fires, GetMe below never completes (not
                    // even with null) - react to the state itself instead of waiting out its
                    // timeout. Deliberately does NOT clear local session data here (unlike
                    // AuthCliRunner) - an unattended service shouldn't auto-wipe a session an
                    // operator might still want to inspect; that stays a manual auth-cli decision.
                    log.error(
                        "Telegram session is being logged out (stale/invalidated remotely) - " +
                            "continuing without Telegram. Run auth-cli to log in again."
                    )
                    loggedOutHandled.set(true)
                    telegramClientHolder.clear()
                    shutdownLatch.countDown()
                }
                is TdApi.AuthorizationStateClosed -> {
                    log.error("Telegram client closed unexpectedly - session may be invalid. Run auth-cli again.")
                    telegramClientHolder.clear()
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
                // call reveals the session was actually ended remotely.
                log.error(
                    "Existing Telegram session is invalid (likely logged out remotely) - " +
                        "continuing without Telegram. Run the auth-cli profile again to log in fresh."
                )
                telegramClientHolder.clear()
                client.sendClose()
                return
            }
            log.info("Confirmed session for {} {} (id={})", me.firstName, me.lastName, me.id)
            sessionRepository.upsertReady(
                phoneNumber = me.phoneNumber,
                telegramUserId = me.id,
                verifiedAt = Instant.now(),
            )
        } catch (ex: Exception) {
            if (!loggedOutHandled.get()) {
                log.error(
                    "Failed to confirm existing Telegram session - continuing without Telegram. " +
                        "Run auth-cli again if this persists.",
                    ex,
                )
                telegramClientHolder.clear()
            }
            // sendClose() is fire-and-forget, unlike closeAndWait() - a hung/incomplete TDLib
            // close handshake here must never block process exit (observed during implementation:
            // a stuck closeAndWait() left the container running indefinitely instead of failing).
            client.sendClose()
            return
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
