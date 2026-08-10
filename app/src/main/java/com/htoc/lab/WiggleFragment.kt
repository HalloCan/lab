package com.htoc.lab

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.sqrt

class WiggleFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var realtimeRight: TextView
    private lateinit var resultTitle: TextView
    private lateinit var resultText: TextView
    private lateinit var checkBox: CheckBox
    private lateinit var btnStart: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var sliderDataSize: Slider
    private lateinit var sliderDataSizeLabel: TextView
    private lateinit var sliderThreshold: Slider
    private lateinit var sliderThresholdLabel: TextView

    private val randomBytes = mutableListOf<Byte>()
    private var targetBytes = 1024
    private var isCollecting = false
    private var isComplete = false

    private var shakeThreshold = 20f
    private var lastShakeTime = 0L
    private var lastMagnitude = 0f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_wiggle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progress_bar)
        realtimeRight = view.findViewById(R.id.text_view_right)
        resultTitle = view.findViewById(R.id.result_title)
        resultText = view.findViewById(R.id.edit_result)
        checkBox = view.findViewById(R.id.check_box)
        btnStart = view.findViewById(R.id.btn_start)
        btnReset = view.findViewById(R.id.btn_reset)
        sliderDataSize = view.findViewById(R.id.slider_data_size)
        sliderDataSizeLabel = view.findViewById(R.id.slider_data_size_label)
        sliderThreshold = view.findViewById(R.id.slider_threshold)
        sliderThresholdLabel = view.findViewById(R.id.slider_threshold_label)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        realtimeRight.setText(" ")

        updateDataSizeLabel(sliderDataSize.value.toInt())
        updateThresholdLabel(sliderThreshold.value.toInt())

        sliderDataSize.addOnChangeListener { _, value, _ ->
            targetBytes = value.toInt()
            updateDataSizeLabel(targetBytes)
        }

        sliderThreshold.addOnChangeListener { _, value, _ ->
            shakeThreshold = value
            updateThresholdLabel(value.toInt())
        }

        btnStart.setOnClickListener { startCollecting() }
        btnReset.setOnClickListener { reset() }
    }

    override fun onPause() {
        super.onPause()
        if (isCollecting && !isComplete) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isCollecting && !isComplete) {
            registerSensor()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isCollecting || isComplete) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z)
        val delta = kotlin.math.abs(magnitude - lastMagnitude)
        lastMagnitude = magnitude

        if (checkBox.isChecked) {
            val bytes = ByteBuffer.allocate(12)
                .putFloat(x).putFloat(y).putFloat(z)
                .array()
            realtimeRight.text = bytes.take(8).joinToString("") { "%02X".format(it) }
        } else {
            val ops = charArrayOf('+', '-', '*', '/')
            realtimeRight.text = (1..16).map { ops.random() }.joinToString("")
        }

        val now = System.currentTimeMillis()
        if (delta > shakeThreshold && now - lastShakeTime > 80) {
            lastShakeTime = now
            onShakeDetected(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startCollecting() {
        isCollecting = true
        btnStart.isEnabled = false
        btnStart.text = getString(R.string.wiggle_collecting_button)
        sliderDataSize.isEnabled = false
        sliderThreshold.isEnabled = false
        registerSensor()
    }

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

        val digest = MessageDigest.getInstance("SHA-256")
        val seedBuf = ByteBuffer.allocate(48)
        seedBuf.putFloat(x).putFloat(y).putFloat(z)
            .putLong(sensorTime).putLong(wallTime).putLong(nanoTime)
        seedBuf.flip()
        digest.update(seedBuf)
        val hash = digest.digest()

        val spaceLeft = targetBytes - randomBytes.size
        val toAdd = minOf(hash.size, spaceLeft)
        for (i in 0 until toAdd) {
            randomBytes.add(hash[i])
        }

        updateProgress()

        if (randomBytes.size >= targetBytes) {
            onComplete()
        }
    }

    private fun updateProgress() {
        val percent = (randomBytes.size * 100) / targetBytes
        progressBar.setProgressCompat(percent, true)
    }

    private fun updateDataSizeLabel(value: Int) {
        sliderDataSizeLabel.text = getString(R.string.wiggle_data_size_label, value)
    }

    private fun updateThresholdLabel(value: Int) {
        sliderThresholdLabel.text = getString(R.string.wiggle_threshold_label, value)
    }

    private fun onComplete() {
        isComplete = true
        sensorManager.unregisterListener(this)

        val digest = MessageDigest.getInstance("SHA-256")
        val finalBuf = ByteBuffer.allocate(randomBytes.size + 16)
        finalBuf.put(randomBytes.toByteArray())
            .putLong(System.currentTimeMillis()).putLong(System.nanoTime())
        finalBuf.flip()
        digest.update(finalBuf)
        val hash = digest.digest()

        val hexValue = hash.take(16).joinToString("") { "%02X".format(it) }

        resultText.text = hexValue
        resultTitle.visibility = View.VISIBLE
        resultText.visibility = View.VISIBLE
        progressBar.setProgressCompat(100, true)
    }

    private fun reset() {
        sensorManager.unregisterListener(this)
        isCollecting = false
        isComplete = false
        randomBytes.clear()
        lastMagnitude = 0f
        progressBar.setProgressCompat(0, false)
        realtimeRight.setText(" ")
        resultTitle.visibility = View.GONE
        resultText.visibility = View.GONE
        sliderDataSize.isEnabled = true
        sliderThreshold.isEnabled = true
        btnStart.isEnabled = true
        btnStart.text = getString(R.string.wiggle_start_button)
    }
}
