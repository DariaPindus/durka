package com.durka.backend.telegram

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("telegram")
data class TelegramProperties(
    val apiId: Int,
    val apiHash: String,
    val databaseDirectory: String = "/data/tdlib",
)
