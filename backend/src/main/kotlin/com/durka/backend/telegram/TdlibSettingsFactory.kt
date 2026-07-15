package com.durka.backend.telegram

import it.tdlight.client.APIToken
import it.tdlight.client.TDLibSettings
import org.springframework.stereotype.Component
import java.nio.file.Paths

@Component
class TdlibSettingsFactory(private val properties: TelegramProperties) {

    // tdlight-java's TDLibSettings has no database-encryption-key setter (verified against its source) -
    // the local session directory is unencrypted at the TDLib layer. It's isolated instead via a named
    // Docker volume (never a host bind mount) plus whatever host-level disk encryption is in place.
    fun create(): TDLibSettings {
        val apiToken = APIToken(properties.apiId, properties.apiHash)
        val settings = TDLibSettings.create(apiToken)
        val sessionDir = Paths.get(properties.databaseDirectory)
        settings.databaseDirectoryPath = sessionDir.resolve("data")
        settings.downloadedFilesDirectoryPath = sessionDir.resolve("downloads")
        // This slice is pure auth-and-idle - no message ingestion yet. TDLibSettings.create()
        // defaults all three to true, which makes tdlight-java auto-trigger a LoadChats() call
        // as soon as authorization is Ready; that call failed in testing and appears to be what
        // left the TDLib close handshake (closeAndWait()) hanging indefinitely afterward.
        settings.isFileDatabaseEnabled = false
        settings.isChatInfoDatabaseEnabled = false
        settings.isMessageDatabaseEnabled = false
        return settings
    }

    /**
     * Clears just TDLib's session/database files (not downloads, not the volume itself) so the
     * next client build starts fresh - needed when a session is logged out remotely (e.g. from
     * within Telegram's own "active sessions" list): TDLib's local binlog doesn't know that
     * happened, so it resumes straight to Ready before the first real API call reveals the
     * session is actually dead. Scoped to the "data" subdirectory only, so a plain volume mount
     * (not a fresh volume) is all that's needed to recover.
     */
    fun clearSessionData() {
        Paths.get(properties.databaseDirectory).resolve("data").toFile().deleteRecursively()
    }
}
