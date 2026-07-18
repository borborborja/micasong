package com.micasong.player.data.backup

import com.micasong.player.data.db.MusicDao
import com.micasong.player.data.db.PlaylistDao
import com.micasong.player.data.db.PlaylistEntity
import com.micasong.player.data.db.ProviderConfigEntity
import com.micasong.player.data.db.ProviderDao
import com.micasong.player.data.settings.SettingsRepository
import com.micasong.player.data.settings.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Serializable form of a media-provider connection (rowId is device-specific, so omitted). */
@Serializable
data class BackupProvider(
    val type: String,
    val displayName: String,
    val primaryUrl: String?,
    val secondaryUrl: String?,
    val username: String?,
    val secret: String?,
    val wifiOnly: Boolean,
    val enabled: Boolean,
    val maxBitrateMobile: Int,
    val maxBitrateWifi: Int,
    val activeConnection: Int,
)

/** Serializable form of a playlist: its name plus the ids of its member tracks in order. */
@Serializable
data class BackupPlaylist(val name: String, val trackIds: List<Long>)

/** Outcome of a restore, so the UI can tell the user what happened (spec §43). */
data class RestoreResult(
    val ok: Boolean,
    val message: String,
    val playlistsRestored: Int = 0,
    val providersRestored: Int = 0,
    val settingsRestored: Boolean = false,
)

/**
 * Orchestrates real backup creation and restoration (spec §43). Gathers the selected content into
 * JSON entries, hands them to [BackupArchive] for zip + encryption, and reverses that on restore.
 * Kept free of Android/UI types so it round-trips under a plain Robolectric DB test.
 */
@Singleton
class BackupManager @Inject constructor(
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val providerDao: ProviderDao,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Build an encrypted archive of [selection], stamped with [appVersion] and [createdAtMs]. */
    suspend fun createBackup(
        selection: Set<BackupContent>,
        password: String,
        appVersion: String,
        createdAtMs: Long,
    ): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()

        if (BackupContent.SETTINGS in selection) {
            entries[SETTINGS_FILE] = json.encodeToString(settings.settings.first()).toByteArray()
        }
        if (BackupContent.PROVIDERS in selection) {
            val providers = providerDao.all().first().map { it.toBackup() }
            entries[PROVIDERS_FILE] = json.encodeToString(providers).toByteArray()
        }
        if (BackupContent.PLAYLISTS in selection) {
            val playlists = playlistDao.playlists().first().map { pl ->
                BackupPlaylist(pl.name, playlistDao.memberTrackIds(pl.id))
            }
            entries[PLAYLISTS_FILE] = json.encodeToString(playlists).toByteArray()
        }

        val manifest = BackupManifest(
            appVersion = appVersion,
            createdAtEpochMs = createdAtMs,
            contents = selection.map { it.name }.toSet(),
        )
        entries[MANIFEST_FILE] = json.encodeToString(manifest).toByteArray()

        return BackupArchive.pack(entries, password)
    }

    /** Decrypt and apply [archive]; returns a failure result if the password is wrong or corrupt. */
    suspend fun restoreBackup(archive: ByteArray, password: String): RestoreResult {
        val files = BackupArchive.unpack(archive, password)
            ?: return RestoreResult(false, "Contraseña incorrecta o archivo dañado")

        var playlists = 0
        var providers = 0
        var settingsRestored = false

        files[SETTINGS_FILE]?.let { bytes ->
            runCatching { json.decodeFromString<UserSettings>(bytes.decodeToString()) }.getOrNull()?.let {
                settings.applySettings(it)
                settingsRestored = true
            }
        }
        files[PROVIDERS_FILE]?.let { bytes ->
            val list = runCatching {
                json.decodeFromString<List<BackupProvider>>(bytes.decodeToString())
            }.getOrDefault(emptyList())
            for (p in list) {
                providerDao.upsert(p.toEntity())
                providers++
            }
        }
        files[PLAYLISTS_FILE]?.let { bytes ->
            val list = runCatching {
                json.decodeFromString<List<BackupPlaylist>>(bytes.decodeToString())
            }.getOrDefault(emptyList())
            for (pl in list) {
                val id = playlistDao.upsert(PlaylistEntity(name = pl.name, providerId = LOCAL_PROVIDER_ID))
                // Only reference tracks that actually exist in this device's library.
                val present = musicDao.tracksByIds(pl.trackIds).map { it.id }.toSet()
                val members = pl.trackIds.filter { it in present }
                playlistDao.setPlaylistTracks(id, members)
                playlistDao.upsert(
                    playlistDao.getPlaylist(id)!!.copy(trackCount = members.size)
                )
                playlists++
            }
        }

        return RestoreResult(
            ok = true,
            message = "Restauración completada",
            playlistsRestored = playlists,
            providersRestored = providers,
            settingsRestored = settingsRestored,
        )
    }

    private fun ProviderConfigEntity.toBackup() = BackupProvider(
        type = type,
        displayName = displayName,
        primaryUrl = primaryUrl,
        secondaryUrl = secondaryUrl,
        username = username,
        secret = secret,
        wifiOnly = wifiOnly,
        enabled = enabled,
        maxBitrateMobile = maxBitrateMobile,
        maxBitrateWifi = maxBitrateWifi,
        activeConnection = activeConnection,
    )

    private fun BackupProvider.toEntity() = ProviderConfigEntity(
        type = type,
        displayName = displayName,
        primaryUrl = primaryUrl,
        secondaryUrl = secondaryUrl,
        username = username,
        secret = secret,
        wifiOnly = wifiOnly,
        enabled = enabled,
        maxBitrateMobile = maxBitrateMobile,
        maxBitrateWifi = maxBitrateWifi,
        activeConnection = activeConnection,
    )

    companion object {
        const val LOCAL_PROVIDER_ID = 1L
        private const val SETTINGS_FILE = "settings.json"
        private const val PROVIDERS_FILE = "providers.json"
        private const val PLAYLISTS_FILE = "playlists.json"
        private const val MANIFEST_FILE = "manifest.json"
    }
}
