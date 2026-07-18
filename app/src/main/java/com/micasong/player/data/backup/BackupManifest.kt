package com.micasong.player.data.backup

import kotlinx.serialization.Serializable

/**
 * The selectable pieces of a backup (spec §43). Each maps 1:1 to a boolean flag of the
 * `start_backup` broadcast API (§42), so an automation can request exactly the same subset a
 * user would tick in the UI.
 */
enum class BackupContent(val apiFlag: String) {
    SETTINGS("SETTINGS"),
    PROVIDERS("PROVIDERS"),
    EQ_PROFILES("EQ_PROFILES"),
    INTERNET_RADIO("INTERNET_RADIO"),
    PLAYLISTS("PLAYLISTS"),
    AUTO_OFFLINE_RULES("AUTO_OFFLINE_RULES"),
    USER_DATA("USER_DATA"),
    USER_DATA_ALL("USER_DATA_ALL"),
    CUSTOM_IMAGES("CUSTOM_IMAGES"),
    FILE_FAVORITES("FILE_FAVORITES"),
    TAG_CACHE("TAG_CACHE"),
}

/**
 * Metadata describing a backup archive (spec §43). The archive itself is always
 * password-encrypted because it may contain provider credentials; this manifest records what
 * was included and how to restore it.
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String,
    val createdAtEpochMs: Long,
    val contents: Set<String>,          // BackupContent names
    val encrypted: Boolean = true,
) {
    fun includes(content: BackupContent): Boolean = content.name in contents

    /** The archive contains secrets whenever providers (credentials) are backed up. */
    val containsSecrets: Boolean get() = includes(BackupContent.PROVIDERS)

    companion object {
        const val EXTENSION = "micabkp"
    }
}

/** Result of validating a backup request before it runs. */
sealed interface BackupValidation {
    data object Ok : BackupValidation
    data class Invalid(val reason: String) : BackupValidation
}

object BackupSelection {

    val ALL: Set<BackupContent> = BackupContent.entries.toSet()

    /** A sensible default that captures config and user state but not heavy caches. */
    val DEFAULT: Set<BackupContent> = setOf(
        BackupContent.SETTINGS,
        BackupContent.PROVIDERS,
        BackupContent.EQ_PROFILES,
        BackupContent.INTERNET_RADIO,
        BackupContent.PLAYLISTS,
        BackupContent.AUTO_OFFLINE_RULES,
        BackupContent.USER_DATA,
        BackupContent.FILE_FAVORITES,
    )

    /** Build a selection from the `start_backup` API boolean extras (spec §42). */
    fun fromApiFlags(flags: Map<String, Boolean>): Set<BackupContent> =
        BackupContent.entries.filter { flags[it.apiFlag] == true }.toSet()

    /** Emit the API boolean extras for a selection. */
    fun toApiFlags(contents: Set<BackupContent>): Map<String, Boolean> =
        BackupContent.entries.associate { it.apiFlag to (it in contents) }

    /**
     * Validate a backup request. The password is mandatory (§43: the archive is always
     * encrypted), and there must be at least one content selected.
     */
    fun validate(contents: Set<BackupContent>, password: String?): BackupValidation = when {
        contents.isEmpty() -> BackupValidation.Invalid("Selecciona al menos un tipo de contenido")
        password.isNullOrBlank() -> BackupValidation.Invalid("La contraseña es obligatoria")
        else -> BackupValidation.Ok
    }
}
