package com.micasong.player.backup

import com.micasong.player.data.backup.BackupArchive
import com.micasong.player.data.backup.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val payload = "provider password: hunter2 · favorites: [1,2,3]".toByteArray()

    @Test
    fun `encrypt then decrypt round trips`() {
        val blob = BackupCrypto.encrypt(payload, "s3cret")
        assertArrayEquals(payload, BackupCrypto.decrypt(blob, "s3cret"))
    }

    @Test
    fun `wrong password fails to decrypt`() {
        val blob = BackupCrypto.encrypt(payload, "correct")
        assertNull(BackupCrypto.decrypt(blob, "wrong"))
    }

    @Test
    fun `tampered ciphertext fails the auth tag`() {
        val blob = BackupCrypto.encrypt(payload, "pw")
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()   // flip a bit in the tag
        assertNull(BackupCrypto.decrypt(blob, "pw"))
    }

    @Test
    fun `each encryption is unique thanks to random salt and iv`() {
        val a = BackupCrypto.encrypt(payload, "pw")
        val b = BackupCrypto.encrypt(payload, "pw")
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `too-short blob returns null`() {
        assertNull(BackupCrypto.decrypt(ByteArray(4), "pw"))
    }

    // ---- Full archive pipeline ----

    @Test
    fun `zip and unzip round trip`() {
        val entries = mapOf(
            "settings.json" to "{}".toByteArray(),
            "providers.json" to "[1,2]".toByteArray(),
        )
        val restored = BackupArchive.unzip(BackupArchive.zip(entries))
        assertEquals(entries.keys, restored.keys)
        assertArrayEquals(entries["settings.json"], restored["settings.json"])
    }

    @Test
    fun `pack and unpack an encrypted archive`() {
        val entries = mapOf("a.txt" to "hello".toByteArray(), "b.bin" to byteArrayOf(0, 1, 2, 3))
        val archive = BackupArchive.pack(entries, "backup-pass")
        val restored = BackupArchive.unpack(archive, "backup-pass")!!
        assertArrayEquals(entries["a.txt"], restored["a.txt"])
        assertArrayEquals(entries["b.bin"], restored["b.bin"])
    }

    @Test
    fun `unpack with wrong password returns null`() {
        val archive = BackupArchive.pack(mapOf("x" to "y".toByteArray()), "right")
        assertNull(BackupArchive.unpack(archive, "nope"))
    }
}
