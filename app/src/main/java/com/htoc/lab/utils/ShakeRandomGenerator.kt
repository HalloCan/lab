package com.htoc.lab.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 基于设备摇晃的随机数生成器。
 *
 * 设计思路（类似 VeraCrypt 鼠标熵收集）：
 *
 * 1. 使用差分数据而非原始值 —— 消除重力和设备姿态的固定偏移。
 *
 * 2. 变化量阈值过滤 —— 丢弃静止/微抖的低熵数据。
 *
 * 3. 量化为 Short (16-bit) 存储 —— 取每个 Int 的低 2 字节，
 *    高位字节是符号扩展（几乎恒定 0x00/0xFF），携带的熵可忽略。
 *    评估与存储使用同一份数据，保持一致性。
 *
 * 4. 基于熵池复杂度停止 —— Shannon 熵 + 位平衡度 + 可预测性测试综合评估。
 *
 * 5. HKDF-HMAC-SHA512 —— 遵循 NIST SP 800-56C 提取器规范。
 */
class ShakeRandomGenerator(
    context: Context,
    private var shakeThreshold: Float = 3f,
    private var qualityThreshold: Int = 85,
    private val onProgress: (score: Int) -> Unit = {},
    private val onComplete: (hexResult: String) -> Unit = {},
    private val onSensorData: ((dx: Int, dy: Int, dz: Int, rawX: Float, rawY: Float, rawZ: Float) -> Unit)? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val collectedBytes = mutableListOf<Byte>()
    private val estimator = EntropyEstimator()
    private var isCollecting = false
    private var isComplete = false

    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var hasPrevSample = false

    val progress: Int get() = estimator.entropyScore
    val isRunning: Boolean get() = isCollecting && !isComplete
    val isFinished: Boolean get() = isComplete

    fun start() {
        if (isCollecting) return
        isCollecting = true
        isComplete = false
        hasPrevSample = false
        registerSensor()
    }

    fun reset() {
        sensorManager.unregisterListener(this)
        isCollecting = false
        isComplete = false
        collectedBytes.clear()
        estimator.reset()
        hasPrevSample = false
        onProgress(0)
    }

    fun updateThreshold(threshold: Float) {
        shakeThreshold = threshold
    }

    fun updateQualityThreshold(threshold: Int) {
        qualityThreshold = threshold
    }

    fun release() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isCollecting || isComplete) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!hasPrevSample) {
            prevX = x; prevY = y; prevZ = z
            hasPrevSample = true
            return
        }

        val dx = x - prevX
        val dy = y - prevY
        val dz = z - prevZ
        prevX = x; prevY = y; prevZ = z

        val delta = abs(dx) + abs(dy) + abs(dz)

        // 量化为 Int，用于 UI 显示和阈值判断
        val qdx = (dx * 1000).roundToInt()
        val qdy = (dy * 1000).roundToInt()
        val qdz = (dz * 1000).roundToInt()

        onSensorData?.invoke(qdx, qdy, qdz, x, y, z)

        if (delta < shakeThreshold) return

        // 存储低 2 字节（Short），与熵评估器使用同一份数据
        // .toShort() 自然截取低 16 位，高位符号扩展被丢弃
        val sdx = qdx.toShort()
        val sdy = qdy.toShort()
        val sdz = qdz.toShort()

        val buf = ByteBuffer.allocate(6)
        buf.putShort(sdx).putShort(sdy).putShort(sdz)
        val data = buf.array()
        for (b in data) {
            collectedBytes.add(b)
        }

        // 评估器与存储使用同一份 Short 数据
        estimator.feed(sdx, sdy, sdz)
        onProgress(estimator.entropyScore)

        if (estimator.isReady && estimator.entropyScore >= qualityThreshold) {
            onCompleteInternal()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun registerSensor() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun onCompleteInternal() {
        isComplete = true
        sensorManager.unregisterListener(this)

        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val ikm = collectedBytes.toByteArray()
        val info = "ShakeRandomGenerator".toByteArray()
        val okm = HkdfSha512.derive(salt, ikm, info, 32)

        val hexValue = okm.joinToString("") { "%02X".format(it) }
        onComplete(hexValue)
    }
}
