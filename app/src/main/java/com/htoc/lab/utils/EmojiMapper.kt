package com.htoc.lab.utils

/**
 * 将随机字节映射为 emoji 序列。
 *
 * 每个字节 (0-255) 通过 mod 64 映射到 64 个精选 emoji 之一，
 * 32 字节随机值 → 32 个 emoji，直观可视化随机结果。
 */
object EmojiMapper {

    private val table = arrayOf(
        // 自然 (16)
        "🌵", "🍄", "🌰", "🌸", "🌿", "🍀", "🍁", "🌾",
        "🌻", "🌺", "🌷", "🌹", "🌊", "🌋", "🌈", "⭐",
        // 水果 (16)
        "🍇", "🍉", "🍊", "🍋", "🍌", "🍍", "🍎", "🍑",
        "🍒", "草莓", "🥝", "🍅", "🥑", "🌽", "🥕", "🌶",
        // 食物 (16)
        "🍞", "🧀", "🍖", "🍗", "🌭", "🍔", "🍟", "🍕",
        "🌮", "🌯", "🥙", "🥚", "🍳", "🥘", "🍲", "🥗",
        // 物品 (16)
        "🍿", "🍩", "🍪", "🎂", "🍰", "🍫", "🍬", "🍭",
        "☕", "🍵", "🥤", "🍶", "🍺", "🍻", "🥂", "🎯"
    )

    /**
     * 将字节数组映射为 emoji 字符串。
     * 每个字节 → 一个 emoji，32 字节 → 32 个 emoji。
     */
    fun map(bytes: ByteArray): String {
        return bytes.joinToString("") { b ->
            table[(b.toInt() and 0xFF) % table.size]
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
}
