package dev.stunmesh.android.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the tunnel config as one JSON blob, encrypted at rest with an
 * AES-256-GCM key that lives in the Android Keystore (the key material never
 * leaves the secure hardware). The config holds the WG private key and the
 * plugin API token, so it must not sit on disk in plain text.
 */
class ConfigRepository(context: Context) {

    private val file = File(context.filesDir, "tunnel_config.bin")

    fun load(): TunnelConfig {
        if (!file.exists()) return TunnelConfig()
        return runCatching {
            TunnelConfig.fromJson(decrypt(file.readBytes()).decodeToString())
        }.getOrDefault(TunnelConfig())
    }

    fun save(config: TunnelConfig) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(encrypt(config.toJson().encodeToByteArray()))
        check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) {
            "config write failed"
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "config blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = GCMParameterSpec(128, blob, 0, IV_SIZE)
        cipher.init(Cipher.DECRYPT_MODE, key(), iv)
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "stunmesh_config"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
