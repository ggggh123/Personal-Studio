package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.knowledge.vm.CategoryWithCount
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Void

@Composable
fun CategoryChipRow(
    items: List<CategoryWithCount>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Chip(label = "全部", selected = selectedId == null) { onSelect(null) }
        items.forEach { (cat, count) ->
            Chip(label = "${cat.name} $count", selected = selectedId == cat.id) { onSelect(cat.id) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) Phosphor else FoamDim
    val text = if (selected) Phosphor else Foam
    Text(
        "[$label]",
        color = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .background(Void)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
