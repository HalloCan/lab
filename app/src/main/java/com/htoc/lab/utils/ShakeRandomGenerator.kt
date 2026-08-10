package com.htoc.lab.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * 基于设备摇晃的随机数生成器。
 *
 * 通过加速度传感器采集设备运动数据，混合多重时间戳作为熵源，
 * 经 SHA-256 哈希累积后输出密码学级别的随机数。
 *
 * 用法:
 * ```kotlin
 * val generator = ShakeRandomGenerator(
 *     context = this,
 *     targetBytes = 1024,
 *     shakeThreshold = 20f,
 *     onProgress = { percent -> /* 更新进度条 */ },
 *     onComplete = { hex -> /* 显示结果 */ }
 * )
 * generator.start()
 * // ...
 * generator.reset()   // 重置，可重新开始
 * generator.release() // 页面销毁时释放
 * ```
 */
class ShakeRandomGenerator(
    context: Context,
    private val targetBytes: Int = 1024,
    private var shakeThreshold: Float = 10f,
    private val onProgress: (percent: Int) -> Unit = {},
    private val onComplete: (hexResult: String) -> Unit = {},
    private val onSensorData: ((x: Float, y: Float, z: Float) -> Unit)? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val collectedBytes = mutableListOf<Byte>()
    private var isCollecting = false
    private var isComplete = false

    private var lastMagnitude = 0f
    private var lastShakeTime = 0L

    val progress: Int get() = if (targetBytes > 0) (collectedBytes.size * 100) / targetBytes else 0
    val isRunning: Boolean get() = isCollecting && !isComplete
    val isFinished: Boolean get() = isComplete

    /**
     * 开始采集摇晃数据。
     * 重复调用无效（需先 reset）。
     */
    fun start() {
        if (isCollecting) return
        isCollecting = true
        isComplete = false
        registerSensor()
    }

    /**
     * 重置状态，可重新开始采集。
     */
    fun reset() {
        sensorManager.unregisterListener(this)
        isCollecting = false
        isComplete = false
        collectedBytes.clear()
        lastMagnitude = 0f
        lastShakeTime = 0L
        onProgress(0)
    }

    /**
     * 更新晃动灵敏度阈值（运行中也可调用）。
     */
    fun updateThreshold(threshold: Float) {
        shakeThreshold = threshold
    }

    /**
     * 释放传感器监听，页面销毁时必须调用。
     */
    fun release() {
        sensorManager.unregisterListener(this)
    }

    // --- SensorEventListener ---

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isCollecting || isComplete) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z)
        val delta = kotlin.math.abs(magnitude - lastMagnitude)
        lastMagnitude = magnitude

        onSensorData?.invoke(x, y, z)

        val now = System.currentTimeMillis()
        if (delta > shakeThreshold && now - lastShakeTime > 80) {
            lastShakeTime = now
            onShakeDetected(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- Internal ---

    private fun registerSensor() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun onShakeDetected(event: SensorEvent) {
        if (isComplete) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val sensorTime = event.timestamp
        val wallTime = System.currentTimeMillis()
        val nanoTime = System.nanoTime()

        // 传感器数据 + 三重时间戳加盐
        val digest = MessageDigest.getInstance("SHA-256")
        val seedBuf = ByteBuffer.allocate(48)
        seedBuf.putFloat(x).putFloat(y).putFloat(z)
            .putLong(sensorTime).putLong(wallTime).putLong(nanoTime)
        seedBuf.flip()
        digest.update(seedBuf)
        val hash = digest.digest()

        val spaceLeft = targetBytes - collectedBytes.size
        val toAdd = minOf(hash.size, spaceLeft)
        for (i in 0 until toAdd) {
            collectedBytes.add(hash[i])
        }

        onProgress(progress)

        if (collectedBytes.size >= targetBytes) {
            onCompleteInternal()
        }
    }

    private fun onCompleteInternal() {
        isComplete = true
        sensorManager.unregisterListener(this)

        // 收集的随机字节 + 最终时间戳二次加盐
        val digest = MessageDigest.getInstance("SHA-256")
        val finalBuf = ByteBuffer.allocate(collectedBytes.size + 16)
        finalBuf.put(collectedBytes.toByteArray())
            .putLong(System.currentTimeMillis())
            .putLong(System.nanoTime())
        finalBuf.flip()
        digest.update(finalBuf)
        val hash = digest.digest()

        val hexValue = hash.take(16).joinToString("") { "%02X".format(it) }
        onComplete(hexValue)
    }
}
