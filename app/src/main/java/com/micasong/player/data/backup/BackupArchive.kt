package com.micasong.player.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * In-memory ZIP packaging for backups (spec §43). A backup is the set of selected content pieces
 * (settings.json, providers.json, playlists.json, the DB, …) zipped together and then encrypted
 * with [BackupCrypto]. Restore reverses it: decrypt → unzip. Pure and unit-testable.
 */
object BackupArchive {

    fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    /** Package + encrypt a set of entries into an encrypted archive (spec §43). */
    fun pack(entries: Map<String, ByteArray>, password: String): ByteArray =
        BackupCrypto.encrypt(zip(entries), password)

    /** Decrypt + unpack an archive, or null if the password is wrong / data is corrupt. */
    fun unpack(archive: ByteArray, password: String): Map<String, ByteArray>? =
        BackupCrypto.decrypt(archive, password)?.let { unzip(it) }
}
