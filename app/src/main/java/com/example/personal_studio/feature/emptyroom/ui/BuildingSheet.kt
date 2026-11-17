package com.example.personal_studio.feature.emptyroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personal_studio.domain.emptyroom.model.EmptyRoomCatalog
import com.example.personal_studio.ui.components.TgBottomSheet
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSegmented
import com.example.personal_studio.ui.theme.Tg
import java.time.Instant
import java.time.ZoneId

/**
 * Choose the place: one campus (segmented when there are up to four), then its buildings in an
 * even three-column grid, with 全部 and recently used buildings as shortcuts. Nothing changes
 * until 完成; switching campus starts from all of its buildings.
 */
@Composable
internal fun BuildingSheet(
    catalog: EmptyRoomCatalog,
    campusCode: String?,
    buildingCodes: Set<String>,
    recent: List<String>,
    onDone: (campusCode: String, buildingCodes: Set<String>) -> Unit,
    onRefreshCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Tg.colors
    var campus by remember { mutableStateOf(campusCode?.takeIf { code -> catalog.campuses.any { it.code == code } } ?: catalog.campuses.firstOrNull()?.code) }
    var codes by remember { mutableStateOf(if (campus == campusCode) buildingCodes else emptySet()) }
    val buildings = catalog.buildingsOf(campus)
    val recentHere = recent.mapNotNull { code -> buildings.firstOrNull { it.code == code } }.take(4)

    TgBottomSheet(onDismiss = onDismiss, header = "选择教学楼") {
        if (catalog.campuses.size in 2..4) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                TgSegmented(
                    options = catalog.campuses.map { shortCampus(it.name) },
                    selectedIndex = catalog.campuses.indexOfFirst { it.code == campus }.coerceAtLeast(0),
                    onSelect = { i -> val next = catalog.campuses[i].code; if (next != campus) { campus = next; codes = emptySet() } },
                    width = maxWidth,
                )
            }
        } else if (catalog.campuses.size > 4) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                catalog.campuses.forEach { cp ->
                    TgChip(shortCampus(cp.name), cp.code == campus, onClick = { if (cp.code != campus) { campus = cp.code; codes = emptySet() } })
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (catalog.campuses.firstOrNull { it.code == campus }?.name ?: "") + " · ${buildings.size} 栋",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f),
            )
            TgChip("全部", codes.isEmpty(), onClick = { codes = emptySet() })
        }

        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            buildings.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        BuildingCell(b.name, b.code in codes, Modifier.weight(1f)) {
                            codes = if (b.code in codes) codes - b.code else codes + b.code
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (buildings.isEmpty()) {
                Text("这个校区没有可查的教学楼。", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }

        if (recentHere.isNotEmpty()) {
            Text("最近用过", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentHere.forEach { b ->
                    TgChip(b.name, b.code in codes, onClick = { codes = if (b.code in codes) codes - b.code else codes + b.code })
                }
            }
        }

        TgPillButton(
            if (codes.isEmpty()) "完成 · 全部 ${buildings.size} 栋" else "完成 · 已选 ${codes.size} 栋",
            onClick = { campus?.let { onDone(it, codes) } },
            enabled = campus != null && buildings.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        val updated = Instant.ofEpochMilli(catalog.fetchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        Text(
            "列表更新于 ${updated.monthValue} 月 ${updated.dayOfMonth} 日 · 重新获取",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "重新获取校区和教学楼") { onRefreshCatalog(); onDismiss() }
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun BuildingCell(name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(if (selected) c.text else Color.Transparent, tween(180), label = "cellBg")
    val fg by animateColorAsState(if (selected) c.bg else c.text, tween(180), label = "cellFg")
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) c.text else c.line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
