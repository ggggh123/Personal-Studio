package com.example.personal_studio.feature.knowledge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.personal_studio.feature.knowledge.vm.CategoryWithCount
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor

/**
 * 分类筛选:扁平标签行(横向滚动)。去掉旧的圆角边框+方括号双重描边,改为
 * 选中=Phosphor 文字 + 下划线,未选=FoamMute 暗字;计数作 `·N` 暗后缀。
 */
@Composable
fun CategoryChipRow(
    items: List<CategoryWithCount>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
        CategoryTab(label = "全部", count = null, selected = selectedId == null) { onSelect(null) }
        items.forEach { (cat, count) ->
            CategoryTab(label = cat.name, count = count, selected = selectedId == cat.id) { onSelect(cat.id) }
        }
    }
}

@Composable
private fun CategoryTab(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 18.dp).clickable(onClick = onClick),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = if (selected) Phosphor else FoamMute)) { append(label) }
                if (count != null) withStyle(SpanStyle(color = FoamDim)) { append("·$count") }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 5.dp),
        )
        // 选中项下方 Phosphor 下划线(宽度随文字);未选为透明,占位保持基线对齐。
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) Phosphor else Color.Transparent),
        )
    }
}
