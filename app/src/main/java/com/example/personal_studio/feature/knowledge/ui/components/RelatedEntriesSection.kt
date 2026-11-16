package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.personal_studio.domain.model.KbEntry
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgSettingRow
import com.example.personal_studio.ui.theme.Tg

/** 相关条目: nothing when [related] is empty, otherwise a card with one row per entry. */
@Composable
fun RelatedEntriesSection(
    related: List<KbEntry>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (related.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text("相关条目", style = MaterialTheme.typography.headlineSmall, color = Tg.colors.text)
        TgCard(Modifier.padding(top = 10.dp)) {
            related.forEachIndexed { i, entry ->
                TgSettingRow(
                    title = entry.title,
                    summary = (entry.categoryName ?: "未分类") + if (entry.isMistake) " · 错题" else " · 笔记",
                    first = i == 0,
                    onClick = { onOpen(entry.id) },
                )
            }
        }
    }
}
