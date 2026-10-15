package com.adriy.wearbyadriy.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("preferred_styles") val preferredStyles: List<String> = emptyList(),
    @SerialName("preferred_colors") val preferredColors: List<String> = emptyList(),
    @SerialName("avoided_colors") val avoidedColors: List<String> = emptyList(),
)

@Serializable
data class ProfileUpdate(
    @SerialName("display_name") val displayName: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("preferred_styles") val preferredStyles: List<String> = emptyList(),
    @SerialName("preferred_colors") val preferredColors: List<String> = emptyList(),
    @SerialName("avoided_colors") val avoidedColors: List<String> = emptyList(),
)

@Serializable
data class WardrobeItem(
    val id: String,
    val name: String,
    val category: String,
    val colors: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val occasions: List<String> = emptyList(),
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val notes: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val mainColor: String? get() = colors.firstOrNull()
}

/** Body for insert/update: no id, user_id or timestamps (the database sets them). */
@Serializable
data class WardrobeItemInput(
    val name: String,
    val category: String,
    val colors: List<String>,
    val styles: List<String>,
    val occasions: List<String>,
    @SerialName("is_available") val isAvailable: Boolean,
    @SerialName("is_favorite") val isFavorite: Boolean,
    val notes: String?,
    @SerialName("image_path") val imagePath: String?,
)

@Serializable
data class LookItemRef(
    @SerialName("item_id") val itemId: String,
    val position: Int = 0,
)

@Serializable
data class Look(
    val id: String,
    val name: String,
    val description: String? = null,
    val reasons: List<String> = emptyList(),
    val occasion: String? = null,
    val tags: List<String> = emptyList(),
    val source: String = "manual",
    @SerialName("ai_provider") val aiProvider: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("worn_dates") val wornDates: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("look_items") val lookItems: List<LookItemRef> = emptyList(),
) {
    val itemIds: List<String> get() = lookItems.sortedBy { it.position }.map { it.itemId }
}

@Serializable
data class LookInput(
    val name: String,
    val description: String?,
    val reasons: List<String>,
    val occasion: String?,
    val tags: List<String>,
    val source: String,
    @SerialName("ai_provider") val aiProvider: String?,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("worn_dates") val wornDates: List<String> = emptyList(),
)

@Serializable
data class LookItemInsert(
    @SerialName("look_id") val lookId: String,
    @SerialName("item_id") val itemId: String,
    val position: Int,
)

@Serializable
data class GenerationLog(
    val provider: String,
    val model: String?,
    val status: String,
    val error: String? = null,
    val occasion: String? = null,
    @SerialName("look_count") val lookCount: Int = 0,
)

/** An outfit suggestion that has not been saved yet. */
data class OutfitSuggestion(
    val name: String,
    val description: String,
    val reasons: List<String>,
    val tags: List<String>,
    val itemIds: List<String>,
    val source: String,          // "ai" or "rules"
    val provider: String? = null,
)
