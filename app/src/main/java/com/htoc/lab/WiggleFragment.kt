package com.htoc.lab

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.google.android.material.snackbar.Snackbar
import com.htoc.lab.utils.ShakeRandomGenerator
import java.nio.ByteBuffer

class WiggleFragment : Fragment() {

    private lateinit var generator: ShakeRandomGenerator

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

        realtimeRight.setText(" ")

        resultText.setOnClickListener {
            val text = resultText.text
            if (text.isNullOrBlank()) return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("random_hex", text))
            Snackbar.make(view, getString(R.string.wiggle_copied), Snackbar.LENGTH_SHORT).show()
        }

        updateDataSizeLabel(sliderDataSize.value.toInt())
        updateThresholdLabel(sliderThreshold.value.toInt())

        sliderDataSize.addOnChangeListener { _, value, _ ->
            updateDataSizeLabel(value.toInt())
        }

        sliderThreshold.addOnChangeListener { _, value, _ ->
            generator.updateThreshold(value)
            updateThresholdLabel(value.toInt())
        }

        generator = ShakeRandomGenerator(
            context = requireContext(),
            targetBytes = sliderDataSize.value.toInt(),
            shakeThreshold = sliderThreshold.value,
            onProgress = { percent ->
                progressBar.setProgressCompat(percent, true)
            },
            onComplete = { hex ->
                resultText.text = hex
                resultTitle.visibility = View.VISIBLE
                resultText.visibility = View.VISIBLE
                progressBar.setProgressCompat(100, true)
            },
            onSensorData = { x, y, z ->
                if (checkBox.isChecked) {
                    val bytes = ByteBuffer.allocate(12)
                        .putFloat(x).putFloat(y).putFloat(z)
                        .array()
                    realtimeRight.text = bytes.take(8).joinToString("") { "%02X".format(it) }
                } else {
                    val ops = charArrayOf('+', '-', '*', '/')
                    realtimeRight.text = (1..16).map { ops.random() }.joinToString("")
                }
            }
        )

        btnStart.setOnClickListener { startCollecting() }
        btnReset.setOnClickListener { reset() }
    }

    override fun onResume() {
        super.onResume()
        if (generator.isRunning) {
            generator.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (generator.isRunning) {
            generator.release()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        generator.release()
    }

    private fun startCollecting() {
        // 用当前滑块值重建 generator
        generator = ShakeRandomGenerator(
            context = requireContext(),
            targetBytes = sliderDataSize.value.toInt(),
            shakeThreshold = sliderThreshold.value,
            onProgress = { percent ->
                progressBar.setProgressCompat(percent, true)
            },
            onComplete = { hex ->
                resultText.text = hex
                resultTitle.visibility = View.VISIBLE
                resultText.visibility = View.VISIBLE
                progressBar.setProgressCompat(100, true)
            },
            onSensorData = { x, y, z ->
                if (checkBox.isChecked) {
                    val bytes = ByteBuffer.allocate(12)
                        .putFloat(x).putFloat(y).putFloat(z)
                        .array()
                    realtimeRight.text = bytes.take(8).joinToString("") { "%02X".format(it) }
                } else {
                    val ops = charArrayOf('+', '-', '*', '/')
                    realtimeRight.text = (1..16).map { ops.random() }.joinToString("")
                }
            }
        )
        generator.start()

        btnStart.isEnabled = false
        btnStart.text = getString(R.string.wiggle_collecting_button)
        sliderDataSize.isEnabled = false
        sliderThreshold.isEnabled = false
    }

    private fun reset() {
        generator.reset()

        progressBar.setProgressCompat(0, false)
        realtimeRight.setText(" ")
        resultTitle.visibility = View.GONE
        resultText.visibility = View.GONE
        sliderDataSize.isEnabled = true
        sliderThreshold.isEnabled = true
        btnStart.isEnabled = true
        btnStart.text = getString(R.string.wiggle_start_button)
    }

    @SuppressLint("StringFormatInvalid")
    private fun updateDataSizeLabel(value: Int) {
        sliderDataSizeLabel.text = getString(R.string.wiggle_data_size_label, value)
    }

    private fun updateThresholdLabel(value: Int) {
        sliderThresholdLabel.text = getString(R.string.wiggle_threshold_label, value)
    }
}
