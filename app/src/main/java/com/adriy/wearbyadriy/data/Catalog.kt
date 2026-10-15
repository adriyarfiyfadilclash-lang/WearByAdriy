package com.adriy.wearbyadriy.data

import androidx.compose.ui.graphics.Color

/** Fixed vocabularies shared by the app, the database checks and the AI prompt. */
object Catalog {
    data class Category(val key: String, val label: String)

    val categories = listOf(
        Category("tops", "Atasan"),
        Category("bottoms", "Bawahan"),
        Category("outerwear", "Outerwear"),
        Category("shoes", "Sepatu"),
        Category("bags", "Tas"),
        Category("accessories", "Aksesori"),
        Category("others", "Lainnya"),
    )
    /** Order used when laying out an outfit, outermost first. */
    val slotOrder = listOf("outerwear", "tops", "bottoms", "shoes", "bags", "accessories", "others")

    fun categoryLabel(key: String) = categories.firstOrNull { it.key == key }?.label ?: key

    data class NamedColor(val name: String, val color: Color, val neutral: Boolean)

    val colors = listOf(
        NamedColor("Hitam", Color(0xFF151515), true),
        NamedColor("Putih", Color(0xFFF4F4F1), true),
        NamedColor("Abu-abu", Color(0xFF8E8E94), true),
        NamedColor("Charcoal", Color(0xFF3C4047), true),
        NamedColor("Beige", Color(0xFFD6C7AC), true),
        NamedColor("Krem", Color(0xFFEDE4D0), true),
        NamedColor("Navy", Color(0xFF1E2B4F), true),
        NamedColor("Cokelat", Color(0xFF6E4A2D), true),
        NamedColor("Olive", Color(0xFF6A6E38), true),
        NamedColor("Biru", Color(0xFF3E6CCF), false),
        NamedColor("Denim", Color(0xFF41608F), false),
        NamedColor("Hijau", Color(0xFF2E6A46), false),
        NamedColor("Merah", Color(0xFFB8312A), false),
        NamedColor("Maroon", Color(0xFF6A1E2D), false),
        NamedColor("Pink", Color(0xFFE2A0B7), false),
        NamedColor("Ungu", Color(0xFF6D4FB4), false),
        NamedColor("Kuning", Color(0xFFE1B83A), false),
        NamedColor("Oranye", Color(0xFFDC7932), false),
    )

    fun color(name: String): Color = colors.firstOrNull { it.name == name }?.color ?: Color(0xFF9A96A6)
    fun isNeutral(name: String?): Boolean = colors.firstOrNull { it.name == name }?.neutral ?: true

    val styles = listOf("Minimal", "Streetwear", "Smart Casual", "Klasik", "Trendy", "Sporty", "Vintage")
    val occasions = listOf("Santai", "Kerja", "Kuliah", "Formal", "Travel", "Hangout", "Olahraga", "Ibadah", "Kondangan", "Kencan")
    val moods = listOf("Santai", "Rapi", "Percaya diri", "Ceria", "Tenang")
}
