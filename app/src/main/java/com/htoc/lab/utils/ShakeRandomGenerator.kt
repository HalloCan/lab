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
 * 3. 量化为 Int (32-bit) 编码 —— 允许高位全 0/FF，
 *    因为最终经过 HKDF 提取，输入空间基数才是抵抗暴力破解的核心。
 *
 * 4. 基于熵池复杂度停止 —— Shannon 熵 + 位平衡度 + 可预测性测试综合评估。
 *
 * 5. HKDF-HMAC-SHA512 —— 遵循 NIST SP 800-56C 提取器规范，
 *    collectedBytes 作为 IKM，SecureRandom 生成的系统熵作为 Salt。
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

        // 量化为 Int (32-bit)，不截断
        val qdx = (dx * 1000).roundToInt()
        val qdy = (dy * 1000).roundToInt()
        val qdz = (dz * 1000).roundToInt()

        onSensorData?.invoke(qdx, qdy, qdz, x, y, z)

        if (delta < shakeThreshold) return

        // 每轴 4 字节 Int，共 12 字节
        val buf = ByteBuffer.allocate(12)
        buf.putInt(qdx).putInt(qdy).putInt(qdz)
        val data = buf.array()
        for (b in data) {
            collectedBytes.add(b)
        }

        estimator.feed(qdx, qdy, qdz)
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

        // HKDF-HMAC-SHA512: 符合 NIST SP 800-56C 提取器规范
        // salt: SecureRandom 生成 32 字节系统熵（每次独立，不存储）
        // ikm:  熵池原始字节
        // info: 上下文绑定，防止跨用途密钥复用
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val ikm = collectedBytes.toByteArray()
        val info = "ShakeRandomGenerator".toByteArray()
        val okm = HkdfSha512.derive(salt, ikm, info, 32)

        val hexValue = okm.joinToString("") { "%02X".format(it) }
        onComplete(hexValue)
    }
}
