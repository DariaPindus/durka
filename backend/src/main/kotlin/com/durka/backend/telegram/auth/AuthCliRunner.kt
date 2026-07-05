package com.durka.backend.telegram.auth

import com.durka.backend.telegram.TdlibSettingsFactory
import com.durka.backend.telegram.repository.TelegramSessionStatusRepository
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.jni.TdApi
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * One-time interactive login handshake. Run via:
 *   docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app
 *
 * AuthenticationSupplier.consoleLogin() drives the whole phone/code/2FA-password prompt sequence
 * itself by reading stdin - no manual AuthorizationState state machine needed here.
 */
@Component
@Profile("auth-cli")
class AuthCliRunner(
    private val clientFactory: SimpleTelegramClientFactory,
    private val settingsFactory: TdlibSettingsFactory,
    private val sessionRepository: TelegramSessionStatusRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AuthCliRunner::class.java)

    override fun run(args: ApplicationArguments) {
        val builder = clientFactory.builder(settingsFactory.create())

        builder.addUpdateHandler(TdApi.UpdateAuthorizationState::class.java) { update ->
            log.info("Authorization state: {}", update.authorizationState.javaClass.simpleName)
        }

        val client = builder.build(AuthenticationSupplier.consoleLogin())

        try {
            val me = client.getMeAsync().get(10, TimeUnit.MINUTES)
            log.info("Authenticated as {} {} (id={})", me.firstName, me.lastName, me.id)
            sessionRepository.upsertReady(
                phoneNumber = me.phoneNumber,
                telegramUserId = me.id,
                verifiedAt = Instant.now(),
            )
            // sendClose() is fire-and-forget, unlike closeAndWait() - observed during
            // implementation that closeAndWait() hangs indefinitely here (a TDLib-internal
            // auto LoadChats() call on Ready fails and the close handshake never completes
            // afterward), which would otherwise leave the container running forever even
            // though the bookkeeping write above already succeeded.
            client.sendClose()
            exitProcess(0)
        } catch (ex: Exception) {
            log.error("Authentication failed", ex)
            client.sendClose()
            exitProcess(1)
        }
    }
}
