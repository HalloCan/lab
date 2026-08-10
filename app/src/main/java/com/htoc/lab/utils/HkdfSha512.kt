package com.htoc.lab.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 5869 HKDF (HMAC-based Extract-and-Expand Key Derivation Function) 实现。
 *
 * 基于 HMAC-SHA512，符合 NIST SP 800-56C 的提取器规范。
 *
 * - Extract: 从输入密钥材料 (IKM) 和盐 (salt) 提取伪随机密钥 (PRK)
 * - Expand:  从 PRK 扩展为指定长度的输出密钥材料 (OKM)
 */
object HkdfSha512 {

    private const val HMAC_ALG = "HmacSHA512"
    private const val HASH_LEN = 64 // SHA-512 输出 64 字节

    /**
     * Extract: PRK = HMAC-SHA512(salt, IKM)
     */
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(salt, HMAC_ALG))
        return mac.doFinal(ikm)
    }

    /**
     * Expand: 从 PRK 生成 length 字节的 OKM。
     *
     * T(0) = empty
     * T(i) = HMAC-SHA512(PRK, T(i-1) || info || i)   (i = 1, 2, ...)
     * OKM  = T(1) || T(2) || ... 取前 length 字节
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) { "HKDF-Expand length too large" }
        require(prk.size >= HASH_LEN) { "PRK too short" }

        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(prk, HMAC_ALG))

        val okm = ByteArray(length)
        var offset = 0
        var counter = 1
        var tPrev = ByteArray(0)

        while (offset < length) {
            mac.reset()
            mac.update(tPrev)
            mac.update(info)
            mac.update(counter.toByte())
            tPrev = mac.doFinal()

            val toCopy = minOf(tPrev.size, length - offset)
            System.arraycopy(tPrev, 0, okm, offset, toCopy)
            offset += toCopy
            counter++
        }
        return okm
    }

    /**
     * 一步完成 Extract + Expand。
     *
     * @param salt  盐（系统熵），用于提取阶段
     * @param ikm   输入密钥材料（熵池数据）
     * @param info  上下文绑定信息，防止跨用途密钥复用
     * @param length 输出长度（字节）
     */
    fun derive(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
