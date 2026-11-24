package de.bgghome.webtrees.nativ.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** [Geheimfach] mit einem AES-Schluessel im Android Keystore (AES-GCM, 256 Bit). */
class KeystoreGeheimfach : Geheimfach {

    @Synchronized
    private fun schluessel(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val erzeuger = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        erzeuger.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return erzeuger.generateKey()
    }

    override fun zu(klar: String): String {
        val c = Cipher.getInstance(VERFAHREN)
        c.init(Cipher.ENCRYPT_MODE, schluessel())
        return b64(c.iv) + ":" + b64(c.doFinal(klar.toByteArray(Charsets.UTF_8)))
    }

    override fun auf(geheim: String): String? = runCatching {
        val (iv, daten) = geheim.split(':', limit = 2).also { require(it.size == 2) }.map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance(VERFAHREN)
        c.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, iv))
        String(c.doFinal(daten), Charsets.UTF_8)
    }.getOrNull()

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "wtand-geraet"
        const val VERFAHREN = "AES/GCM/NoPadding"
    }
}
