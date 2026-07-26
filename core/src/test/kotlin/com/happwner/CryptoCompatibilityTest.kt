package com.happwner

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CryptoCompatibilityTest {
    @Test
    fun decryptsCryptThroughCrypt4WithEveryBundledKey() {
        val plaintext = "https://example.com/subscription?a=1&b=2"
        val modes = listOf("crypt", "crypt2", "crypt3", "crypt4")

        modes.forEachIndexed { ordinal, mode ->
            val privateKey = invokePrivateKey(HappCrypto, "loadPkcs1Key", ordinal)
            val encrypted = rsaEncrypt(publicKey(privateKey), plaintext.toByteArray())
            val link = "happ://$mode/${Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)}"
            val result = assertIs<HappCrypto.HappLinkResult.Decrypted>(HappCrypto.decryptHappLink(link))

            assertEquals(plaintext, result.plaintext)
            assertEquals(mode, result.mode)
        }
    }

    @Test
    fun decryptsCrypt5WithEveryBundledKeyAndJvmChaCha() {
        val plaintext = "https://example.com/crypt5-subscription"
        val rsaValue = ByteArray(32) { (it * 7 + 3).toByte() }
        val nonce = "123456789012".toByteArray(Charsets.US_ASCII)
        val rsaPlaintext = swapPairs(Base64.getEncoder().encodeToString(rsaValue))
            .toByteArray(Charsets.ISO_8859_1)
        val finalBase64 = Base64.getEncoder().encodeToString(plaintext.toByteArray())
        val chachaPlaintext = swapPairs(finalBase64).toByteArray(Charsets.ISO_8859_1)
        val chacha = Cipher.getInstance("ChaCha20-Poly1305").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(rsaValue, "ChaCha20"),
                IvParameterSpec(nonce),
            )
        }
        val encryptedBody = Base64.getEncoder().encodeToString(chacha.doFinal(chachaPlaintext))

        val markers = crypt5Markers()
        assertEquals(36, markers.size)
        markers.forEach { marker ->
            val privateKey = invokePrivateKey(HappCrypto, "loadPkcs8Key", marker)
            val encryptedKey = Base64.getEncoder().encodeToString(
                rsaEncrypt(publicKey(privateKey), rsaPlaintext),
            )
            val body = "123456789012${encryptedBody.length}.$encryptedBody$encryptedKey"
            val shuffled = marker.take(4) + body + marker.takeLast(4)
            val link = "happ://crypt5/${blockPairSwap(shuffled)}"

            val result = assertIs<HappCrypto.HappLinkResult.Decrypted>(HappCrypto.decryptHappLink(link))
            assertEquals(plaintext, result.plaintext)
            assertEquals("crypt5", result.mode)
        }
    }

    @Test
    fun decryptsV2RayTunWithEveryBundledKey() {
        val plaintext = "https://example.com/v2raytun-subscription"

        repeat(3) { ordinal ->
            val privateKey = invokePrivateKey(V2RayTunCrypto, "loadKey", ordinal)
            val encrypted = rsaEncrypt(publicKey(privateKey), plaintext.toByteArray())
            val link = "v2raytun://crypt/${Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)}"
            val result = assertIs<V2RayTunCrypto.Result.Decrypted>(V2RayTunCrypto.decryptCryptLink(link))

            assertEquals(plaintext, result.plaintext)
        }
    }

    @Test
    fun decryptsAesGcmSubscriptionBodyWithEveryBundledKey() {
        val plaintext = "vless://profile"
        val iv = "kkkkkkkkkkkk".toByteArray(Charsets.US_ASCII)

        val keys = subscriptionKeys()
        assertEquals(10, keys.size)
        keys.forEach { (keyName, key) ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            val encryptedWithTag = cipher.doFinal(plaintext.toByteArray())
            val ciphertext = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - 16)
            val tag = encryptedWithTag.copyOfRange(encryptedWithTag.size - 16, encryptedWithTag.size)

            val result = assertIs<HappCrypto.Result.Success>(
                HappCrypto.process(
                    "https://example.com/sub?key=$keyName",
                    Base64.getEncoder().encodeToString(ciphertext),
                    mapOf("encrypt-tag" to listOf(Base64.getEncoder().encodeToString(tag))),
                ),
            )

            assertEquals(plaintext, result.plaintext)
        }
    }

    private fun invokePrivateKey(target: Any, methodName: String, argument: Any): PrivateKey {
        val argumentType = if (argument is Int) Int::class.javaPrimitiveType else String::class.java
        val method = target::class.java.getDeclaredMethod(methodName, argumentType).apply {
            isAccessible = true
        }
        return method.invoke(target, argument) as PrivateKey
    }

    @Suppress("UNCHECKED_CAST")
    private fun crypt5Markers(): Set<String> {
        val field = HappCrypto::class.java.getDeclaredField("CRYPT5_PKCS8_KEYS_B64").apply {
            isAccessible = true
        }
        return (field.get(HappCrypto) as Map<String, String>).keys
    }

    @Suppress("UNCHECKED_CAST")
    private fun subscriptionKeys(): Map<String, ByteArray> {
        val field = HappCrypto::class.java.getDeclaredField("KEYS").apply {
            isAccessible = true
        }
        return field.get(HappCrypto) as Map<String, ByteArray>
    }

    private fun publicKey(privateKey: PrivateKey): PublicKey {
        val rsa = privateKey as RSAPrivateCrtKey
        return KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(rsa.modulus, rsa.publicExponent),
        )
    }

    private fun rsaEncrypt(publicKey: PublicKey, plaintext: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.ENCRYPT_MODE, publicKey)
            doFinal(plaintext)
        }

    private fun swapPairs(value: String): String {
        val result = value.toCharArray()
        var index = 0
        while (index + 1 < result.size) {
            val first = result[index]
            result[index] = result[index + 1]
            result[index + 1] = first
            index += 2
        }
        return String(result)
    }

    private fun blockPairSwap(value: String): String {
        val result = value.toCharArray()
        val fullLength = value.length - value.length % 4
        var index = 0
        while (index < fullLength) {
            result[index] = value[index + 2]
            result[index + 1] = value[index + 3]
            result[index + 2] = value[index]
            result[index + 3] = value[index + 1]
            index += 4
        }
        return String(result)
    }
}
