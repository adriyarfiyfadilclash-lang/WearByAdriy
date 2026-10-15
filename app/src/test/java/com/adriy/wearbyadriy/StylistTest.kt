package com.adriy.wearbyadriy

import com.adriy.wearbyadriy.ai.StyleRequest
import com.adriy.wearbyadriy.ai.Stylist
import com.adriy.wearbyadriy.data.WardrobeItem
import com.adriy.wearbyadriy.data.Weather
import com.adriy.wearbyadriy.ui.auth.passwordProblem
import com.adriy.wearbyadriy.util.UserError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StylistTest {
    private fun item(id: String, cat: String, color: String = "Hitam", available: Boolean = true) =
        WardrobeItem(id = id, name = "Item $id", category = cat, colors = listOf(color), isAvailable = available)

    private val wardrobe = listOf(
        item("t1", "tops", "Putih"), item("t2", "tops", "Merah"), item("b1", "bottoms", "Navy"),
        item("b2", "bottoms", "Beige"), item("s1", "shoes"), item("o1", "outerwear", "Charcoal"),
        item("x1", "tops", "Hitam", available = false),
    )
    private val req = StyleRequest(occasion = "Santai", styles = listOf("Minimal"))

    @Test fun rulesUseOnlyRealAvailableItemsAndAlwaysTopAndBottom() {
        val looks = Stylist.ruleBased(wardrobe, req, seed = 1)
        assertTrue(looks.isNotEmpty())
        val ids = wardrobe.filter { it.isAvailable }.map { it.id }.toSet()
        looks.forEach { l ->
            assertTrue(l.itemIds.all { it in ids })
            val cats = l.itemIds.map { id -> wardrobe.first { it.id == id }.category }
            assertTrue("tops" in cats && "bottoms" in cats)
            assertEquals("rules", l.source)
        }
    }

    @Test fun noOutfitWithoutBottoms() {
        assertTrue(Stylist.ruleBased(wardrobe.filter { it.category != "bottoms" }, req).isEmpty())
    }

    @Test fun avoidedColorsAreSkipped() {
        val looks = Stylist.ruleBased(wardrobe, req.copy(avoidedColors = listOf("Merah")), seed = 3)
        assertTrue(looks.none { "t2" in it.itemIds })
    }

    @Test fun pinnedItemIsUsed() {
        val looks = Stylist.ruleBased(wardrobe, req.copy(pinnedItemIds = listOf("t2")), seed = 5)
        assertTrue(looks.all { "t2" in it.itemIds })
    }

    @Test fun parseDropsInventedIdsAndIncompleteLooks() {
        val text = """Here: {"looks":[
            {"name":"A","description":"d","reasons":["r"],"tags":["x"],"item_ids":["t1","b1","fake"]},
            {"name":"B","item_ids":["t1","s1"]},
            {"name":"C","item_ids":["x1","b2"]}]}"""
        val looks = Stylist.parse(text, wardrobe, req, "OpenAI")
        assertEquals(1, looks.size)
        assertEquals(listOf("t1", "b1"), looks[0].itemIds)
        assertEquals("ai", looks[0].source)
    }

    @Test(expected = UserError::class) fun parseFailsWhenNothingValid() {
        Stylist.parse("""{"looks":[{"name":"Z","item_ids":["nope","b1"]}]}""", wardrobe, req, "Gemini")
    }

    @Test(expected = UserError::class) fun parseFailsOnNonJson() {
        Stylist.parse("Sorry, I can't help with that.", wardrobe, req, "Claude")
    }

    @Test fun weatherCondition() {
        assertEquals("Hujan", Weather(29.0, 24.0, 31.0, 63).condition)
        assertEquals("Panas", Weather(32.0, 26.0, 33.0, 1).condition)
        assertEquals("Dingin", Weather(15.0, 12.0, 18.0, 0).condition)
    }

    @Test fun passwordRules() {
        assertNotNull(passwordProblem("short1", "short1"))
        assertNotNull(passwordProblem("abcdefgh", "abcdefgh"))
        assertNotNull(passwordProblem("abcdefg1", "abcdefg2"))
        assertNull(passwordProblem("abcdefg1", "abcdefg1"))
    }
}
