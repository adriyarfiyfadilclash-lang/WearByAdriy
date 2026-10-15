package com.adriy.wearbyadriy.ai

import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.data.OutfitSuggestion
import com.adriy.wearbyadriy.data.WardrobeItem
import com.adriy.wearbyadriy.util.UserError
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlin.random.Random

data class StyleRequest(
    val occasion: String,
    val styles: List<String>,
    val preferredColors: List<String> = emptyList(),
    val avoidedColors: List<String> = emptyList(),
    val weather: String? = null,      // Panas / Hangat / Sejuk / Dingin / Hujan
    val mood: String? = null,
    val pinnedItemIds: List<String> = emptyList(),
    val count: Int = 3,
)

/** Builds outfits only from the user's real wardrobe, with AI or with transparent rules. */
object Stylist {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun pool(items: List<WardrobeItem>, req: StyleRequest): List<WardrobeItem> = items.filter { item ->
        item.isAvailable && (item.mainColor !in req.avoidedColors || item.id in req.pinnedItemIds)
    }

    fun canStyle(pool: List<WardrobeItem>) =
        pool.any { it.category == "tops" } && pool.any { it.category == "bottoms" }

    // ---------------- rules ----------------

    fun ruleBased(items: List<WardrobeItem>, req: StyleRequest, seed: Long = System.currentTimeMillis()): List<OutfitSuggestion> {
        val pool = pool(items, req)
        if (!canStyle(pool)) return emptyList()
        val rnd = Random(seed)
        val styles = req.styles.ifEmpty { listOf("Minimal") }
        fun score(i: WardrobeItem): Double {
            var s = 0.0
            if (i.occasions.isEmpty() || req.occasion in i.occasions) s += 2
            if (styles.any { it in i.styles }) s += 2
            if (i.mainColor in req.preferredColors) s += 3
            if (Catalog.isNeutral(i.mainColor)) s += 1
            if (i.isFavorite) s += 0.5
            return s + rnd.nextDouble() * 1.6
        }
        val byCat = Catalog.categories.associate { c ->
            var list = pool.filter { it.category == c.key }.sortedByDescending { score(it) }
            val pinned = list.filter { it.id in req.pinnedItemIds }
            if (pinned.isNotEmpty()) list = pinned
            c.key to list
        }
        fun hasPinned(cat: String) = byCat[cat].orEmpty().any { it.id in req.pinnedItemIds }
        val formal = req.occasion in listOf("Kerja", "Formal", "Kondangan", "Ibadah")
        val cool = req.weather in listOf("Sejuk", "Dingin", "Hujan")
        val hot = req.weather == "Panas"

        val looks = mutableListOf<OutfitSuggestion>()
        val seen = mutableSetOf<String>()
        val usedNames = mutableSetOf<String>()
        var k = 0
        while (looks.size < req.count && k < req.count * 4 + 4) {
            fun pick(cat: String, partner: WardrobeItem? = null): WardrobeItem? {
                val list = byCat[cat].orEmpty()
                if (list.isEmpty()) return null
                for (j in list.indices) {
                    val it = list[(k + j) % list.size]
                    if (partner != null && !Catalog.isNeutral(it.mainColor) && !Catalog.isNeutral(partner.mainColor)) continue
                    return it
                }
                return list[k % list.size]
            }
            val style = styles[k % styles.size]
            val top = pick("tops")
            val bottom = pick("bottoms", top)
            val wantOuter = if (hot) formal else (cool || formal || style in listOf("Smart Casual", "Klasik", "Streetwear") || k % 2 == 1)
            val outer = if (wantOuter || hasPinned("outerwear")) pick("outerwear", top) else null
            val shoes = pick("shoes")
            val bag = if (req.occasion == "Travel" || k % 3 == 2 || hasPinned("bags")) pick("bags") else null
            val acc = if (k % 3 == 0 || hasPinned("accessories")) pick("accessories") else null
            val other = if (hasPinned("others")) pick("others") else null
            val chosen = listOfNotNull(outer, top, bottom, shoes, bag, acc, other)
            k++
            val key = chosen.map { it.id }.sorted().joinToString()
            if (!seen.add(key)) continue
            val name = (NAMES[style] ?: NAMES.getValue("Minimal")).plus(EXTRA).firstOrNull { it !in usedNames } ?: "Look ${looks.size + 1}"
            usedNames += name
            looks += OutfitSuggestion(
                name = name,
                description = describe(chosen, req),
                reasons = reasons(chosen, req, style),
                tags = listOfNotNull(req.occasion, style, req.mood).distinct().take(3),
                itemIds = chosen.map { it.id },
                source = "rules",
            )
        }
        return looks
    }

    private val NAMES = mapOf(
        "Minimal" to listOf("Clean Minimal", "Quiet Neutrals", "Easy Minimal"),
        "Streetwear" to listOf("Minimal Streetwear", "Urban Layers", "Street Ease"),
        "Smart Casual" to listOf("Smart Casual", "Sharp & Easy", "Weekday Polish"),
        "Klasik" to listOf("Timeless Classic", "Classic Neutrals", "Heritage Mix"),
        "Trendy" to listOf("Fresh Edit", "Statement Mix", "New Season"),
        "Sporty" to listOf("Active Ease", "Sport Luxe", "Weekend Sporty"),
        "Vintage" to listOf("Retro Ease", "Vintage Layers", "Old School"),
    )
    private val EXTRA = listOf("Easy Day", "Fresh Mix", "Clean Lines", "City Walk", "Soft Layers", "Calm Tones")

    private fun describe(items: List<WardrobeItem>, req: StyleRequest): String {
        val names = items.filter { it.category in listOf("outerwear", "tops", "bottoms", "shoes") }.map { it.name.lowercase() }
        val list = if (names.size > 1) names.dropLast(1).joinToString(", ") + " dan " + names.last() else names.firstOrNull() ?: "pakaianmu"
        val accent = items.any { !Catalog.isNeutral(it.mainColor) }
        val sb = StringBuilder("Perpaduan $list.")
        sb.append(if (accent) " Satu warna aksen membuatnya lebih hidup." else " Warna netral membuatnya mudah dipadukan dan tetap rapi.")
        sb.append(" Cocok untuk ${req.occasion.lowercase()}")
        req.weather?.let { sb.append(" saat cuaca ${it.lowercase()}") }
        sb.append(".")
        return sb.toString()
    }

    private fun reasons(items: List<WardrobeItem>, req: StyleRequest, style: String): List<String> {
        val r = mutableListOf<String>()
        val accent = items.firstOrNull { !Catalog.isNeutral(it.mainColor) }
        r += if (accent != null) "Warna netral dipadu aksen ${accent.mainColor?.lowercase()}" else "Warna netral mudah dipadukan"
        if (items.any { req.occasion in it.occasions }) r += "Item ditandai cocok untuk ${req.occasion.lowercase()}"
        else r += "Kombinasi aman untuk ${req.occasion.lowercase()}"
        if (items.any { style in it.styles }) r += "Ada item bergaya ${style.lowercase()} sesuai pilihanmu"
        else if (items.any { it.category == "outerwear" }) r += "Lapisan luar menambah kesan rapi"
        req.weather?.let { w -> if (w in listOf("Sejuk", "Dingin", "Hujan") && items.any { it.category == "outerwear" }) r += "Outerwear membantu saat cuaca $w".lowercase().replaceFirstChar { it.uppercase() } }
        return r.take(3)
    }

    // ---------------- AI ----------------

    fun prompt(items: List<WardrobeItem>, req: StyleRequest): String {
        val pool = pool(items, req)
        val lines = pool.joinToString("\n") { i ->
            "${i.id} | ${i.category} | ${i.name} | ${i.colors.joinToString("/").ifEmpty { "-" }} | styles: ${i.styles.joinToString(", ").ifEmpty { "-" }} | occasions: ${i.occasions.joinToString(", ").ifEmpty { "any" }}"
        }
        val pinned = req.pinnedItemIds.filter { id -> pool.any { it.id == id } }
        return """
You are a personal stylist. Build ${req.count} different outfits using ONLY the clothes listed below (the user's own wardrobe).
Occasion: ${req.occasion}
Preferred styles: ${req.styles.joinToString(", ").ifEmpty { "no preference" }}
Preferred colors: ${req.preferredColors.joinToString(", ").ifEmpty { "no preference" }}
Colors to avoid: ${req.avoidedColors.joinToString(", ").ifEmpty { "none" }}
Weather: ${req.weather ?: "unknown"} | Mood: ${req.mood ?: "any"}
(Values above are in Indonesian.)

Rules:
- Each outfit has exactly one item from "tops" and one from "bottoms". Add "shoes" when available. "outerwear", "bags", "accessories" and "others" are optional; add them only when they suit the occasion and weather.
- Prefer two neutral colors plus at most one accent color.
- The outfits must be clearly different from each other.
- Use the exact item ids. Never invent items.
- name: 2-3 words in English, title case.
- description: 1-2 short sentences in Indonesian explaining why it works.
- reasons: 2-3 very short reasons in Indonesian.
- tags: 2-3 short tags.
${if (pinned.isNotEmpty()) "- The user wants to wear these item ids: ${pinned.joinToString(", ")}. Use each of them in at least one outfit.\n" else ""}
Wardrobe (id | category | name | colors | styles | occasions):
$lines

Reply with only JSON in this shape:
{"looks":[{"name":"Minimal Streetwear","description":"...","reasons":["..."],"tags":["Santai","Minimal"],"item_ids":["id1","id2","id3"]}]}
""".trim()
    }

    @Serializable
    private data class AiLook(
        val name: String = "",
        val description: String = "",
        val reasons: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        @SerialName("item_ids") val itemIds: List<String> = emptyList(),
    )

    @Serializable
    private data class AiResponse(val looks: List<AiLook> = emptyList())

    /** Parses the model's reply and keeps only outfits that reference real, available items. */
    fun parse(text: String, items: List<WardrobeItem>, req: StyleRequest, provider: String): List<OutfitSuggestion> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw UserError("Jawaban AI tidak berisi data outfit yang bisa dibaca.")
        val parsed = runCatching { json.decodeFromString(AiResponse.serializer(), text.substring(start, end + 1)) }
            .getOrElse { throw UserError("Jawaban AI tidak sesuai format. Coba generate lagi.") }
        val byId = pool(items, req).associateBy { it.id }
        val looks = parsed.looks.mapNotNull { l ->
            val seenCat = mutableMapOf<String, Int>()
            val ids = l.itemIds.distinct().filter { id ->
                val item = byId[id] ?: return@filter false
                val n = (seenCat[item.category] ?: 0) + 1
                seenCat[item.category] = n
                n <= if (item.category == "accessories") 2 else 1
            }
            val cats = ids.mapNotNull { byId[it]?.category }
            if ("tops" !in cats || "bottoms" !in cats) return@mapNotNull null
            OutfitSuggestion(
                name = l.name.take(40).ifBlank { "Look" },
                description = l.description.take(300),
                reasons = l.reasons.map { it.take(100) }.filter { it.isNotBlank() }.take(3),
                tags = l.tags.map { it.take(20) }.filter { it.isNotBlank() }.take(3),
                itemIds = ids.sortedBy { id -> Catalog.slotOrder.indexOf(byId[id]?.category) },
                source = "ai",
                provider = provider,
            )
        }.take(req.count)
        if (looks.isEmpty()) throw UserError("Saran AI tidak cocok dengan isi lemarimu (item tidak dikenal atau tidak lengkap). Coba generate lagi.")
        return looks
    }
}
