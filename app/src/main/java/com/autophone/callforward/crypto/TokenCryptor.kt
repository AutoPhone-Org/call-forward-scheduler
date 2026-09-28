package com.autophone.callforward.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感凭证加解密器：基于 Android Keystore 的 AES-256-GCM。
 *
 * 设计要点：
 *  - 主密钥生成于 AndroidKeyStore，**不可导出**（即使设备被 root/备份提取，
 *    密文离开本机也无法解密），这是选择 Keystore 而非硬编码密钥的原因。
 *  - 每次加密使用随机 IV（12 字节），密文格式：`enc:v1:` + Base64(IV + cipherText)。
 *  - [decrypt] 对不带前缀的旧明文原样返回，用于存量明文数据的平滑迁移；
 *    迁移后由调用方以密文形式回写。
 *
 * 线程安全：Cipher 实例在方法内创建，无共享可变状态。
 */
object TokenCryptor {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "call_forward_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    /** 密文前缀（含版本号，便于将来升级算法） */
    private const val PREFIX = "enc:v1:"

    /** 加密明文，返回 `enc:v1:` + Base64(IV + cipherText)；空串原样返回。 */
    fun encrypt(plain: String): String {
        if (plain.isBlank()) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            iv.copyInto(combined)
            encrypted.copyInto(combined, iv.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // 加密失败不应导致功能中断：降级为明文由调用方决定（本应用选择不落盘）
            throw IllegalStateException("加密失败", e)
        }
    }

    /**
     * 解密 `enc:v1:` 开头的密文；不带头前缀的旧明文原样返回（存量迁移路径）。
     * 空串原样返回。
     */
    fun decrypt(stored: String): String {
        if (stored.isBlank()) return stored
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val cipherText = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            // 常见于：密文来自其他设备（Keystore 密钥不可迁移）或数据损坏。
            // 视为解密失败，返回空串让调用方要求重新配置令牌。
            ""
        }
    }

    /** 判断存储值是否已是密文 */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /** 获取或懒创建 Keystore 主密钥 */
    private fun obtainKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
