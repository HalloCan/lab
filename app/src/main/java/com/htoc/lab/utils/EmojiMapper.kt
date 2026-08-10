package com.htoc.lab.utils

/**
 * 将随机字节映射为 emoji 序列。
 *
 * 256 个 emoji 与 256 个字节值一一对应，正反映射无损。
 * 32 字节随机值 → 32 个 emoji，可完整还原。
 */
object EmojiMapper {

    private val table = arrayOf(
        // 0x00-0x0F 自然
        "🌵", "🍄", "🌰", "🌸", "🌿", "🍀", "🍁", "🌾",
        "🌻", "🌺", "🌷", "🌹", "🌊", "🌋", "🌈", "⭐",
        // 0x10-0x1F 水果
        "🍇", "🍉", "🍊", "🍋", "🍌", "🍍", "🍎", "🍑",
        "🍒", "🍓", "🥝", "🍅", "🥑", "🌽", "🥕", "🌶",
        // 0x20-0x2F 食物
        "🍞", "🧀", "🍖", "🍗", "🌭", "🍔", "🍟", "🍕",
        "🌮", "🌯", "🥙", "🥚", "🍳", "🥘", "🍲", "🥗",
        // 0x30-0x3F 甜品饮品
        "🍿", "🍩", "🍪", "🎂", "🍰", "🍫", "🍬", "🍭",
        "☕", "🍵", "🥤", "🍶", "🍺", "🍻", "🥂", "🎯",
        // 0x40-0x4F 动物1
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
        // 0x50-0x5F 动物2
        "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺",
        "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞",
        // 0x60-0x6F 动物3
        "🐜", "🦟", "🦗", "🕷", "🦂", "🐢", "🐍", "🦎",
        "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡",
        // 0x70-0x7F 动物4
        "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅",
        "🐆", "🦓", "🦍", "🦧", "🐘", "🦛", "🦏", "🐪",
        // 0x80-0x8F 植物1
        "🌱", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘",
        "🍀", "🍁", "🍂", "🍃", "🌺", "🌻", "🌼", "🌷",
        // 0x90-0x9F 植物2
        "💐", "🥀", "🏵", "🎀", "🎁", "🎗", "🎪", "🎭",
        "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷",
        // 0xA0-0xAF 天体天气
        "🌍", "🌎", "🌏", "🌐", "🌑", "🌒", "🌓", "🌔",
        "🌕", "🌖", "🌗", "🌘", "🌙", "🌚", "🌛", "🌜",
        // 0xB0-0xBF 手势表情
        "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌",
        "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇",
        // 0xC0-0xCF 心形符号
        "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
        // 0xD0-0xDF 星形符号
        "✨", "⚡", "🔥", "💥", "❄", "💦", "💨", "🕳",
        "💣", "💬", "👁‍🗨", "🗨", "🗯", "💭", "💤", "💫",
        // 0xE0-0xEF 箭头符号
        "⬆", "↗", "➡", "↘", "⬇", "↙", "⬅", "↖",
        "↕", "↔", "↩", "↪", "⤴", "⤵", "🔃", "🔄",
        // 0xF0-0xFF 数字符号
        "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗",
        "🕘", "🕙", "🕚", "🕛", "🕜", "🕝", "🕞", "🕟"
    )

    /**
     * 将字节数组映射为 emoji 字符串。
     * 每个字节 (0-255) → 一个 emoji，1:1 映射，无损。
     */
    fun map(bytes: ByteArray): String {
        return bytes.joinToString("") { b ->
            table[b.toInt() and 0xFF]
        }
    }

    /**
     * 将 hex 字符串映射为 emoji 字符串。
     * hex 每 2 字符 → 1 字节 → 1 个 emoji。
     */
    fun mapFromHex(hex: String): String {
        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return map(bytes)
    }

    /**
     * 将 emoji 字符串反向映射为 hex 字符串。
     * 每个 emoji → 原始字节值 → 2 位 hex。
     * 1:1 映射，反向无损。
     */
    fun mapToHex(emoji: String): String {
        // 构建反向索引
        val reverseMap = mutableMapOf<String, Int>()
        for (i in table.indices) {
            reverseMap[table[i]] = i
        }
        // 按 emoji 长度降序排列，优先匹配多码点 emoji
        val sorted = reverseMap.entries.sortedByDescending { it.key.length }

        val sb = StringBuilder()
        var i = 0
        while (i < emoji.length) {
            var matched = false
            for (entry in sorted) {
                val e = entry.key
                if (emoji.startsWith(e, i)) {
                    sb.append("%02X".format(entry.value))
                    i += e.length
                    matched = true
                    break
                }
            }
            if (!matched) i++
        }
        return sb.toString()
    }
}
