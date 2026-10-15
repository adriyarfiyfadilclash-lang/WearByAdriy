package com.adriy.wearbyadriy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adriy.wearbyadriy.data.Catalog
import com.adriy.wearbyadriy.data.WardrobeItem

/** Displays an outfit made of real wardrobe items. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutfitCard(
    name: String,
    description: String?,
    items: List<WardrobeItem>,
    reasons: List<String>,
    tags: List<String>,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onItemClick: ((WardrobeItem) -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (badge != null) {
                Eyebrow(badge)
                Spacer(Modifier.height(4.dp))
            }
            Text(name, style = MaterialTheme.typography.titleLarge)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text("Item di look ini sudah dihapus dari lemari.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.id }) { item ->
                        Column(Modifier.width(96.dp)) {
                            val m = Modifier.fillMaxWidth().aspectRatio(0.85f)
                            if (onItemClick != null) Surface(onClick = { onItemClick(item) }, shape = MaterialTheme.shapes.small) { ItemImage(item, m, 12.dp) }
                            else ItemImage(item, m, 12.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Catalog.categoryLabel(item.category), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (reasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                reasons.forEach { r ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(6.dp))
                        Text(r, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { Pill(it) }
                }
            }
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}
