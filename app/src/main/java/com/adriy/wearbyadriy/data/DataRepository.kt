package com.adriy.wearbyadriy.data

import android.content.Context
import android.net.Uri
import com.adriy.wearbyadriy.util.ImageCompressor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

/** All reads and writes go through Supabase; Row Level Security limits them to the signed-in user. */
class DataRepository(private val client: SupabaseClient, private val context: Context) {

    // ---------- profile ----------
    suspend fun profile(userId: String): Profile? =
        client.from("profiles").select { filter { eq("id", userId) } }.decodeSingleOrNull<Profile>()

    suspend fun updateProfile(userId: String, update: ProfileUpdate) {
        client.from("profiles").update(update) { filter { eq("id", userId) } }
    }

    // ---------- wardrobe ----------
    suspend fun items(): List<WardrobeItem> =
        client.from("wardrobe_items").select { order("created_at", Order.DESCENDING) }.decodeList<WardrobeItem>()

    suspend fun addItem(input: WardrobeItemInput): WardrobeItem =
        client.from("wardrobe_items").insert(input) { select() }.decodeSingle<WardrobeItem>()

    suspend fun updateItem(id: String, input: WardrobeItemInput): WardrobeItem =
        client.from("wardrobe_items").update(input) {
            select()
            filter { eq("id", id) }
        }.decodeSingle<WardrobeItem>()

    suspend fun setItemFavorite(id: String, value: Boolean) {
        client.from("wardrobe_items").update({ set("is_favorite", value) }) { filter { eq("id", id) } }
    }

    suspend fun setItemAvailable(id: String, value: Boolean) {
        client.from("wardrobe_items").update({ set("is_available", value) }) { filter { eq("id", id) } }
    }

    suspend fun deleteItem(item: WardrobeItem) {
        client.from("wardrobe_items").delete { filter { eq("id", item.id) } }
        item.imagePath?.let { path -> runCatching { deleteImage(path) } }
    }

    // ---------- looks ----------
    suspend fun looks(): List<Look> =
        client.from("looks").select(Columns.raw("*, look_items(item_id, position)")) {
            order("created_at", Order.DESCENDING)
        }.decodeList<Look>()

    suspend fun saveLook(s: OutfitSuggestion, occasion: String?, wornDates: List<String> = emptyList()): Look {
        val look = client.from("looks").insert(
            LookInput(
                name = s.name, description = s.description, reasons = s.reasons, occasion = occasion,
                tags = s.tags, source = s.source, aiProvider = s.provider, wornDates = wornDates,
            )
        ) { select() }.decodeSingle<Look>()
        try {
            setLookItems(look.id, s.itemIds)
        } catch (e: Exception) {
            runCatching { deleteLook(look.id) }   // do not leave an empty look behind
            throw e
        }
        return look.copy(lookItems = s.itemIds.mapIndexed { i, id -> LookItemRef(id, i) })
    }

    suspend fun setLookItems(lookId: String, itemIds: List<String>) {
        client.from("look_items").delete { filter { eq("look_id", lookId) } }
        if (itemIds.isNotEmpty()) {
            client.from("look_items").insert(itemIds.mapIndexed { i, id -> LookItemInsert(lookId, id, i) })
        }
    }

    suspend fun renameLook(id: String, name: String, description: String?) {
        client.from("looks").update({
            set("name", name)
            set("description", description)
        }) { filter { eq("id", id) } }
    }

    suspend fun setLookFavorite(id: String, value: Boolean) {
        client.from("looks").update({ set("is_favorite", value) }) { filter { eq("id", id) } }
    }

    suspend fun setWornDates(id: String, dates: List<String>) {
        client.from("looks").update({ set("worn_dates", dates) }) { filter { eq("id", id) } }
    }

    suspend fun deleteLook(id: String) {
        client.from("looks").delete { filter { eq("id", id) } }
    }

    suspend fun logGeneration(log: GenerationLog) {
        runCatching { client.from("ai_generations").insert(log) }
    }

    // ---------- images (private bucket, path = <user id>/<uuid>.jpg) ----------
    private val signedUrls = ConcurrentHashMap<String, Pair<String, Long>>()

    suspend fun uploadImage(userId: String, uri: Uri): String {
        val bytes = ImageCompressor.compress(context, uri)
        val path = "$userId/${UUID.randomUUID()}.jpg"
        client.storage.from(Backend.BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        return path
    }

    suspend fun signedUrl(path: String): String {
        val now = System.currentTimeMillis()
        signedUrls[path]?.let { (url, expires) -> if (expires - now > 5 * 60_000) return url }
        val url = client.storage.from(Backend.BUCKET).createSignedUrl(path, 1.hours)
        signedUrls[path] = url to (now + 60 * 60_000)
        return url
    }

    suspend fun deleteImage(path: String) {
        client.storage.from(Backend.BUCKET).delete(listOf(path))
        signedUrls.remove(path)
    }

    fun clearCaches() = signedUrls.clear()
}
