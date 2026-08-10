package com.htoc.lab.utils

import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 熵池复杂度评估器。
 *
 * 评估与存储使用同一份 Short (16-bit) 数据，保持一致性。
 *
 * 评估维度：
 *
 * 1. Shannon 熵 — 字节值分布的信息熵，理论最大 8.0 bits/byte。
 *
 * 2. 位平衡度 — 每个 bit 位上 1 的比例，理想值 50%。
 *
 * 3. 可预测性测试 — RLE 压缩比 + 自相关检测周期性重复倾向。
 *    自相关比较信号的变化趋势而非绝对值，即使每次摇晃数值不同，
 *    只要运动模式（如"左→右→左→右"）一致，就能在对应 lag 处得到高相关系数。
 *
 * 4. 最小采样数 — 统计显著性安全下限。
 *
 * 综合评分 = Shannon×40 + 位平衡×20 + 可预测性×40
 */
class EntropyEstimator(
    private val minSampleBytes: Int = 384
) {
    private val histogram = LongArray(256)
    private val bitCounts = IntArray(16)
    private var totalBytes = 0L
    private var totalSamples = 0

    // 环形缓冲区存储最近的原始字节
    private val ringBuffer = ByteArray(min(512, 1024))
    private var ringPos = 0
    private var ringCount = 0

    val sampleCount: Long get() = totalBytes
    val isReady: Boolean get() = totalBytes >= minSampleBytes

    /**
     * 喂入一个采样点的量化差分数据（3 个 Short，共 6 字节）。
     */
    fun feed(dx: Short, dy: Short, dz: Short) {
        val buf = java.nio.ByteBuffer.allocate(6)
        buf.putShort(dx).putShort(dy).putShort(dz)
        val data = buf.array()
        for (b in data) {
            histogram[b.toInt() and 0xFF]++
            ringBuffer[ringPos] = b
            ringPos = (ringPos + 1) % ringBuffer.size
            if (ringCount < ringBuffer.size) ringCount++
        }
        totalBytes += data.size

        countBits16(dx.toInt() and 0xFFFF)
        countBits16(dy.toInt() and 0xFFFF)
        countBits16(dz.toInt() and 0xFFFF)
        totalSamples += 3
    }

    fun reset() {
        histogram.fill(0)
        bitCounts.fill(0)
        ringBuffer.fill(0)
        ringPos = 0
        ringCount = 0
        totalBytes = 0
        totalSamples = 0
    }

    /**
     * 综合熵质量评分，范围 [0, 100]。
     */
    val entropyScore: Int
        get() {
            if (totalBytes < minSampleBytes) {
                // 平方根曲线：早期爬坡快，越接近目标越慢，避免到达瞬间的跳变
                val ratio = totalBytes.toDouble() / minSampleBytes
                return (kotlin.math.sqrt(ratio) * 50).toInt().coerceIn(0, 50)
            }
            val shannonRatio = (shannonEntropy / 8.0).coerceIn(0.0, 1.0)
            val balanceScore = (1.0 - 2.0 * abs(bitBalance - 0.5)).coerceIn(0.0, 1.0)
            val predictScore = predictabilityScore
            return (shannonRatio * 30 + balanceScore * 30 + predictScore * 40)
                .toInt().coerceIn(0, 100)
        }

    /**
     * Shannon 熵，单位 bits/byte。
     */
    val shannonEntropy: Double
        get() {
            if (totalBytes == 0L) return 0.0
            var entropy = 0.0
            for (count in histogram) {
                if (count == 0L) continue
                val p = count.toDouble() / totalBytes
                entropy -= p * ln(p) / ln(2.0)
            }
            return entropy
        }

    /**
     * 位平衡度：每个 bit 位上 1 的平均比例，理想值 0.5。
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
     * 可预测性评分，范围 [0.0, 1.0]。
     *
     * 取 RLE 和自相关中更严格的（更低的）：
     * - RLE: 检测完全相同的连续字节重复
     * - 自相关: 检测周期性趋势重复（如匀速来回摇晃）
     */
    val predictabilityScore: Double
        get() {
            if (ringCount < 64) return 0.5

            val rleScore = ((2.2 - rleCompressionRatio()) / 1.2).coerceIn(0.0, 1.0)
            val autoScore = (1.0 - maxAutocorrelation).coerceIn(0.0, 1.0)
            return min(rleScore, autoScore)
        }

    // --- Internal ---

    /**
     * 最大自相关系数，范围 [0.0, 1.0]。
     *
     * 扫描 lag ∈ [2, N/4]，计算归一化自相关系数，取最大值。
     * 高值表示存在周期性重复倾向（如匀速来回摇晃）。
     */
    private val maxAutocorrelation: Double
        get() {
            if (ringCount < 16) return 0.0

            val data = extractOrdered()
            val n = data.size
            if (n < 16) return 0.0

            val mean = data.sumOf { it.toDouble() } / n
            // 预计算方差分母
            var variance = 0.0
            for (v in data) {
                val d = v - mean
                variance += d * d
            }
            if (variance == 0.0) return 1.0  // 所有值相同，完全可预测

            val maxLag = n / 4
            var maxR = 0.0

            for (lag in 2..maxLag) {
                var numerator = 0.0
                var varA = 0.0
                var varB = 0.0
                val count = n - lag
                for (i in 0 until count) {
                    val a = data[i] - mean
                    val b = data[i + lag] - mean
                    numerator += a * b
                    varA += a * a
                    varB += b * b
                }
                val denom = sqrt(varA * varB)
                if (denom > 0.0) {
                    val r = numerator / denom
                    if (r > maxR) maxR = r
                }
            }
            return maxR
        }

    /**
     * 从环形缓冲区提取有序数组（最旧→最新）。
     */
    private fun extractOrdered(): ByteArray {
        val data = ByteArray(ringCount)
        val start = (ringPos - ringCount + ringBuffer.size) % ringBuffer.size
        for (i in 0 until ringCount) {
            data[i] = ringBuffer[(start + i) % ringBuffer.size]
        }
        return data
    }

    private fun countBits16(value: Int) {
        for (i in 0 until 16) {
            if ((value ushr i) and 1 == 1) {
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
