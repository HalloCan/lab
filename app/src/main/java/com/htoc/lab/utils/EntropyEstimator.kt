package com.htoc.lab.utils

import kotlin.math.ln
import kotlin.math.abs

/**
 * 熵池复杂度评估器。
 *
 * 评估维度：
 *
 * 1. Shannon 熵 — 字节值分布的信息熵，理论最大 8.0 bits/byte。
 *    仅统计每个 Int 的低 2 字节（实际有变化的部分），
 *    高位字节虽存入熵池但不参与评估（它们是符号扩展，几乎恒定）。
 *
 * 2. 位平衡度 — 低 16 位中每个 bit 位上 1 的比例，理想值 50%。
 *
 * 3. 可预测性测试 — 对低字节做 RLE 压缩，压缩比越高说明越有规律。
 *
 * 4. 最小采样数 — 统计显著性安全下限。
 *
 * 综合评分 = Shannon×40 + 位平衡×20 + 可预测性×40
 */
class EntropyEstimator(
    private val minSampleBytes: Int = 1024
) {
    // 256 桶直方图，仅统计低字节
    private val histogram = LongArray(256)
    // 16 个 bit 位计数器（每个 Int 的低 16 位）
    private val bitCounts = IntArray(16)
    private var lowByteCount = 0L
    private var totalSamples = 0

    // 环形缓冲区，仅存储低字节
    private val ringBuffer = ByteArray(256)
    private var ringPos = 0
    private var ringCount = 0

    // 每个 Int 低 2 字节的有效字节数
    private val bytesPerSample = 6  // 3 轴 × 2 字节低字节

    val sampleCount: Long get() = lowByteCount
    val isReady: Boolean get() = lowByteCount >= minSampleBytes

    /**
     * 喂入一个采样点的量化差分数据（3 个 Int）。
     * 仅将每个 Int 的低 2 字节用于熵评估。
     */
    fun feed(dx: Int, dy: Int, dz: Int) {
        // 提取每个 Int 的低 2 字节（大端序 Short 部分）
        val lo0 = (dx and 0xFF).toByte()
        val lo1 = ((dx ushr 8) and 0xFF).toByte()
        val lo2 = (dy and 0xFF).toByte()
        val lo3 = ((dy ushr 8) and 0xFF).toByte()
        val lo4 = (dz and 0xFF).toByte()
        val lo5 = ((dz ushr 8) and 0xFF).toByte()

        val lowBytes = byteArrayOf(lo0, lo1, lo2, lo3, lo4, lo5)
        for (b in lowBytes) {
            histogram[b.toInt() and 0xFF]++
            ringBuffer[ringPos] = b
            ringPos = (ringPos + 1) % ringBuffer.size
            if (ringCount < ringBuffer.size) ringCount++
        }
        lowByteCount += lowBytes.size

        // 位计数只统计低 16 位
        countBits16(dx)
        countBits16(dy)
        countBits16(dz)
        totalSamples += 3
    }

    fun reset() {
        histogram.fill(0)
        bitCounts.fill(0)
        ringBuffer.fill(0)
        ringPos = 0
        ringCount = 0
        lowByteCount = 0
        totalSamples = 0
    }

    /**
     * 综合熵质量评分，范围 [0, 100]。
     */
    val entropyScore: Int
        get() {
            if (lowByteCount < minSampleBytes) {
                return (lowByteCount * 50 / minSampleBytes).toInt()
            }
            val shannonRatio = (shannonEntropy / 8.0).coerceIn(0.0, 1.0)
            val balanceScore = (1.0 - 2.0 * abs(bitBalance - 0.5)).coerceIn(0.0, 1.0)
            val predictScore = predictabilityScore
            return (shannonRatio * 40 + balanceScore * 20 + predictScore * 40)
                .toInt().coerceIn(0, 100)
        }

    /**
     * Shannon 熵（仅低字节），单位 bits/byte。
     */
    val shannonEntropy: Double
        get() {
            if (lowByteCount == 0L) return 0.0
            var entropy = 0.0
            for (count in histogram) {
                if (count == 0L) continue
                val p = count.toDouble() / lowByteCount
                entropy -= p * ln(p) / ln(2.0)
            }
            return entropy
        }

    /**
     * 位平衡度（仅低 16 位）：每个 bit 位上 1 的平均比例，理想值 0.5。
     */
    val bitBalance: Double
        get() {
            if (totalSamples == 0) return 0.0
            val totalBits = totalSamples.toLong() * 16
            var totalOnes = 0L
            for (count in bitCounts) {
                totalOnes += count
            }
            return totalOnes.toDouble() / totalBits
        }

    /**
     * 可预测性评分（仅低字节），范围 [0.0, 1.0]。
     *
     * 对环形缓冲区中的低字节做 RLE 压缩：
     * - 压缩比 ≈ 1.0 → 高度随机 → 评分 1.0
     * - 压缩比 >> 1.0 → 有规律 → 评分趋近 0
     */
    val predictabilityScore: Double
        get() {
            if (ringCount < 128) return 1.0

            val ratio = rleCompressionRatio()
            // ratio=1.0 → 完全随机, ratio=3.0 → 高度可压缩
            return ((3.0 - ratio) / 2.0).coerceIn(0.0, 1.0)
        }

    // --- Internal ---

    private fun countBits16(value: Int) {
        val v = value and 0xFFFF
        for (i in 0 until 16) {
            if ((v ushr i) and 1 == 1) {
                bitCounts[i]++
            }
        }
    }

    private fun rleCompressionRatio(): Double {
        if (ringCount == 0) return 1.0

        val start = (ringPos - ringCount + ringBuffer.size) % ringBuffer.size
        var compressedLen = 0
        var prev = ringBuffer[start]
        var runLen = 1

        for (i in 1 until ringCount) {
            val idx = (start + i) % ringBuffer.size
            val cur = ringBuffer[idx]
            if (cur == prev && runLen < 255) {
                runLen++
            } else {
                compressedLen += 2
                prev = cur
                runLen = 1
            }
        }
        compressedLen += 2

        return ringCount.toDouble() / compressedLen
    }
}
