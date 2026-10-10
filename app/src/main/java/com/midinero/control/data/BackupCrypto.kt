package com.midinero.control.data

import com.midinero.control.domain.tr
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** La copia está cifrada y falta la contraseña o no es la correcta. */
class BackupPasswordException(message: String) : IllegalArgumentException(message)

/**
 * Cifrado opcional de la copia de seguridad con una contraseña: AES-256-GCM con la clave derivada por
 * PBKDF2-HMAC-SHA256. El fichero sigue siendo un JSON («encrypted»: 1) para reconocerlo al restaurar.
 */
object BackupCrypto {
    private const val APP = "MiDinero"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun isEncrypted(text: String): Boolean =
        runCatching { JSONObject(text).optInt("encrypted", 0) == 1 }.getOrDefault(false)

    fun encrypt(plain: String, password: String, iterations: Int = ITERATIONS): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return JSONObject()
            .put("app", APP)
            .put("encrypted", 1)
            .put("kdf", KDF)
            .put("iterations", iterations)
            .put("salt", b64.encodeToString(salt))
            .put("iv", b64.encodeToString(iv))
            .put("data", b64.encodeToString(data))
            .toString(2)
    }

    /** Descifra una copia. Lanza [BackupPasswordException] si falta la contraseña o no es la correcta. */
    fun decrypt(text: String, password: String?): String {
        if (password.isNullOrEmpty()) throw BackupPasswordException(tr("La copia está protegida con contraseña", "The backup is password protected"))
        val o = JSONObject(text)
        require(o.optString("kdf", KDF) == KDF) { tr("La copia es de una versión más nueva de MiDinero", "The backup is from a newer version of MiDinero") }
        val b64 = Base64.getDecoder()
        val salt = b64.decode(o.getString("salt"))
        val iv = b64.decode(o.getString("iv"))
        val data = b64.decode(o.getString("data"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, o.optInt("iterations", ITERATIONS)), GCMParameterSpec(TAG_BITS, iv))
        val plain = try {
            cipher.doFinal(data)
        } catch (_: AEADBadTagException) {
            throw BackupPasswordException(tr("La contraseña no es correcta", "Wrong password"))
        }
        return plain.toString(Charsets.UTF_8)
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
