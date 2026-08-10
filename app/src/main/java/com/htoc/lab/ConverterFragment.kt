package com.htoc.lab

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.htoc.lab.utils.EmojiMapper

class ConverterFragment : Fragment() {

    private lateinit var inputHex: TextInputEditText
    private lateinit var inputEmoji: TextInputEditText
    private lateinit var previewEmoji: android.widget.TextView
    private lateinit var previewHex: android.widget.TextView

    private var isUpdating = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_converter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputHex = view.findViewById(R.id.input_hex)
        inputEmoji = view.findViewById(R.id.input_emoji)
        previewEmoji = view.findViewById(R.id.preview_emoji)
        previewHex = view.findViewById(R.id.preview_hex)

        inputHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val hex = s?.toString()?.trim() ?: ""
                if (hex.isEmpty()) {
                    previewEmoji.text = ""
                    return
                }
                // 过滤非 hex 字符
                val cleanHex = hex.replace(Regex("[^0-9A-Fa-f]"), "")
                // 偶数长度才转换
                if (cleanHex.length % 2 != 0) {
                    previewEmoji.text = ""
                    return
                }
                val emoji = EmojiMapper.mapFromHex(cleanHex)
                previewEmoji.text = emoji
            }
        })

        inputEmoji.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val emoji = s?.toString()?.trim() ?: ""
                if (emoji.isEmpty()) {
                    previewHex.text = ""
                    return
                }
                val hex = EmojiMapper.mapToHex(emoji)
                previewHex.text = hex
            }
        })
    }
}
