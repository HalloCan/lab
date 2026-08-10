package com.htoc.lab.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 基于设备摇晃的随机数生成器。
 *
 * 设计思路（类似 VeraCrypt 鼠标熵收集）：
 *
 * 1. 使用差分数据而非原始值 —— 相邻采样点的加速度差分 (dx, dy, dz)
 *    可以消除重力分量和设备姿态带来的固定偏移，只保留用户运动产生的随机性。
 *
 * 2. 变化量阈值过滤 —— 当 abs(dx)+abs(dy)+abs(dz) 低于阈值时丢弃采样，
 *    避免静止或微抖状态下引入低熵数据。
 *
 * 3. 量化为 milli-g 整数 —— 将 float 乘以 1000 取整，消除跨设备浮点精度差异，
 *    保证同一物理运动在不同设备上产生一致的编码。
 *
 * 4. SHA-512 仅用于最终压缩 —— SHA 是确定性函数，对已有熵做哈希不会增加熵，
 *    只是将累积的熵池数据压缩为固定长度输出。熵的质量完全取决于用户摇晃行为的不可预测性。
 *
 * 用法:
 * ```kotlin
 * val generator = ShakeRandomGenerator(
 *     context = this,
 *     targetBytes = 1024,
 *     shakeThreshold = 3f,
 *     onProgress = { percent -> /* 更新进度条 */ },
 *     onComplete = { hex -> /* 显示结果 */ }
 * )
 * generator.start()
 * ```
 */
class ShakeRandomGenerator(
    context: Context,
    private val targetBytes: Int = 1024,
    private var shakeThreshold: Float = 3f,
    private val onProgress: (percent: Int) -> Unit = {},
    private val onComplete: (hexResult: String) -> Unit = {},
    private val onSensorData: ((dx: Int, dy: Int, dz: Int, rawX: Float, rawY: Float, rawZ: Float) -> Unit)? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val collectedBytes = mutableListOf<Byte>()
    private var isCollecting = false
    private var isComplete = false

    // 差分采样：记录上一帧原始值
    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var hasPrevSample = false

    val progress: Int get() = if (targetBytes > 0) (collectedBytes.size * 100) / targetBytes else 0
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
        hasPrevSample = false
        onProgress(0)
    }

    fun updateThreshold(threshold: Float) {
        shakeThreshold = threshold
    }

    fun release() {
        sensorManager.unregisterListener(this)
    }

    // --- SensorEventListener ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isCollecting || isComplete) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 第一帧只记录基准值，不入池
        if (!hasPrevSample) {
            prevX = x
            prevY = y
            prevZ = z
            hasPrevSample = true
            return
        }

        // 计算差分，消除重力和设备姿态的固定偏移
        val dx = x - prevX
        val dy = y - prevY
        val dz = z - prevZ
        prevX = x
        prevY = y
        prevZ = z

        val delta = abs(dx) + abs(dy) + abs(dz)

        // 将差分量化为 milli-g 整数，保证跨设备一致性
        val qdx = (dx * 1000).roundToInt()
        val qdy = (dy * 1000).roundToInt()
        val qdz = (dz * 1000).roundToInt()

        onSensorData?.invoke(qdx, qdy, qdz, x, y, z)

        // 变化量过小则丢弃，避免静止/微抖引入低熵数据
        if (delta < shakeThreshold) return

        // 每轴 4 字节 Int（大端序），共 12 字节直接追加到熵池
        // 不对单样本做 SHA —— SHA 是确定性函数，不增加熵，只做压缩
        val buf = ByteBuffer.allocate(12)
        buf.putInt(qdx).putInt(qdy).putInt(qdz)

        val spaceLeft = targetBytes - collectedBytes.size
        val data = buf.array()
        val toAdd = minOf(data.size, spaceLeft)
        for (i in 0 until toAdd) {
            collectedBytes.add(data[i])
        }

        onProgress(progress)

        if (collectedBytes.size >= targetBytes) {
            onCompleteInternal()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Internal ---

    private fun registerSensor() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun onCompleteInternal() {
        isComplete = true
        sensorManager.unregisterListener(this)

        // SHA-512 一次性压缩全部熵池 + 时间戳盐
        // SHA 不增加熵，仅将累积的随机数据压缩为固定长度输出
        val digest = MessageDigest.getInstance("SHA-512")
        val saltBuf = ByteBuffer.allocate(16)
        saltBuf.putLong(System.currentTimeMillis()).putLong(System.nanoTime())
        saltBuf.flip()
        digest.update(collectedBytes.toByteArray())
        digest.update(saltBuf)
        val hash = digest.digest()

        // 取前 32 字节 (256-bit) 输出
        val hexValue = hash.take(32).joinToString("") { "%02X".format(it) }
        onComplete(hexValue)
    }
}
