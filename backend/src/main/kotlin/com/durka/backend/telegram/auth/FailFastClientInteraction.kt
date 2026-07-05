package com.durka.backend.telegram.auth

import it.tdlight.client.ClientInteraction
import it.tdlight.client.InputParameter
import it.tdlight.client.ParameterInfo
import java.util.concurrent.CompletableFuture

/**
 * tdlight-java falls back to reading stdin (via Scanner) for any parameter it isn't given up front.
 * A detached container has no stdin attached, so that fallback would just hang forever instead of
 * failing - this makes an unauthenticated session fail fast with a clear error instead.
 */
class FailFastClientInteraction : ClientInteraction {
    override fun onParameterRequest(parameter: InputParameter, parameterInfo: ParameterInfo): CompletableFuture<String> =
        CompletableFuture.failedFuture(
            IllegalStateException(
                "Telegram requested interactive input ($parameter) but no valid session exists. " +
                    "Run the auth-cli profile: docker compose run --rm -it -e SPRING_PROFILES_ACTIVE=auth-cli app"
            )
        )
}
