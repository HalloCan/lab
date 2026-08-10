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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.htoc.lab.utils.EmojiMapper
import com.htoc.lab.utils.ShakeRandomGenerator
import java.nio.ByteBuffer

class WiggleFragment : Fragment() {

    private lateinit var generator: ShakeRandomGenerator

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var realtimeRight: TextView
    private lateinit var resultCard: MaterialCardView
    private lateinit var resultText: TextView
    private lateinit var emojiResult: TextView
    private lateinit var checkBox: CheckBox
    private lateinit var btnStart: MaterialButton
    private lateinit var btnReset: MaterialButton
    private lateinit var sliderThreshold: Slider
    private lateinit var sliderThresholdLabel: TextView
    private lateinit var sliderQuality: Slider
    private lateinit var sliderQualityLabel: TextView

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
        resultCard = view.findViewById(R.id.result_card)
        resultText = view.findViewById(R.id.edit_result)
        emojiResult = view.findViewById(R.id.emoji_result)
        checkBox = view.findViewById(R.id.check_box)
        btnStart = view.findViewById(R.id.btn_start)
        btnReset = view.findViewById(R.id.btn_reset)
        sliderThreshold = view.findViewById(R.id.slider_threshold)
        sliderThresholdLabel = view.findViewById(R.id.slider_threshold_label)
        sliderQuality = view.findViewById(R.id.slider_quality)
        sliderQualityLabel = view.findViewById(R.id.slider_quality_label)

        realtimeRight.setText(" ")

        resultText.setOnClickListener {
            val text = resultText.text
            if (text.isNullOrBlank()) return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("random_hex", text))
            Snackbar.make(view, getString(R.string.wiggle_copied), Snackbar.LENGTH_SHORT).show()
        }

        emojiResult.setOnClickListener {
            val text = emojiResult.text
            if (text.isNullOrBlank()) return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("random_emoji", text))
            Snackbar.make(view, getString(R.string.wiggle_copied), Snackbar.LENGTH_SHORT).show()
        }

        updateThresholdLabel(sliderThreshold.value.toInt())
        updateQualityLabel(sliderQuality.value.toInt())

        sliderThreshold.addOnChangeListener { _, value, _ ->
            updateThresholdLabel(value.toInt())
        }

        sliderQuality.addOnChangeListener { _, value, _ ->
            updateQualityLabel(value.toInt())
        }

        generator = createGenerator()

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

    private fun createGenerator(): ShakeRandomGenerator {
        return ShakeRandomGenerator(
            context = requireContext(),
            shakeThreshold = sliderThreshold.value,
            qualityThreshold = sliderQuality.value.toInt(),
            onProgress = { score ->
                progressBar.setProgressCompat(score, true)
            },
            onComplete = { hex ->
                val emoji = EmojiMapper.mapFromHex(hex)
                resultText.text = hex
                emojiResult.text = emoji
                resultCard.visibility = View.VISIBLE
                progressBar.setProgressCompat(100, true)
                showResultDialog(hex, emoji)
            },
            onSensorData = { dx, dy, dz, _, _, _ ->
                if (checkBox.isChecked) {
                    val buf = ByteBuffer.allocate(12)
                    buf.putInt(dx).putInt(dy).putInt(dz)
                    realtimeRight.text = buf.array().take(8).joinToString("") { "%02X".format(it) }
                } else {
                    val ops = charArrayOf('+', '-', '*', '/')
                    realtimeRight.text = (1..16).map { ops.random() }.joinToString("")
                }
            }
        )
    }

    private fun startCollecting() {
        generator = createGenerator()
        generator.start()

        btnStart.isEnabled = false
        btnStart.text = getString(R.string.wiggle_collecting_button)
        sliderThreshold.isEnabled = false
        sliderQuality.isEnabled = false
    }

    private fun reset() {
        generator.reset()

        progressBar.setProgressCompat(0, false)
        realtimeRight.setText(" ")
        resultCard.visibility = View.GONE
        sliderThreshold.isEnabled = true
        sliderQuality.isEnabled = true
        btnStart.isEnabled = true
        btnStart.text = getString(R.string.wiggle_start_button)
    }

    private fun showResultDialog(hex: String, emoji: String) {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 16)
        }

        val hexLabel = TextView(requireContext()).apply {
            text = getString(R.string.wiggle_result_title)
            setTextAppearance(com.google.android.material.R.attr.textAppearanceLabelLarge)
        }

        val hexView = TextView(requireContext()).apply {
            text = hex
            setTextAppearance(com.google.android.material.R.attr.textAppearanceTitleMedium)
            setTextColor(requireContext().getColor(com.google.android.material.R.color.m3_sys_color_dynamic_light_primary))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 32)
            setOnClickListener {
                copyToClipboard("random_hex", hex)
            }
        }

        val emojiLabel = TextView(requireContext()).apply {
            text = getString(R.string.wiggle_emoji_title)
            setTextAppearance(com.google.android.material.R.attr.textAppearanceLabelLarge)
        }

        val emojiView = TextView(requireContext()).apply {
            text = emoji
            textSize = 24f
            setPadding(0, 8, 0, 0)
            setOnClickListener {
                copyToClipboard("random_emoji", emoji)
            }
        }

        layout.addView(hexLabel)
        layout.addView(hexView)
        layout.addView(emojiLabel)
        layout.addView(emojiView)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wiggle_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.wiggle_dialog_ok, null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Snackbar.make(requireView(), getString(R.string.wiggle_copied), Snackbar.LENGTH_SHORT).show()
    }

    private fun updateThresholdLabel(value: Int) {
        sliderThresholdLabel.text = getString(R.string.wiggle_threshold_label, value)
    }

    @SuppressLint("StringFormatInvalid")
    private fun updateQualityLabel(value: Int) {
        sliderQualityLabel.text = getString(R.string.wiggle_quality_label, value)
    }
}
