package com.example.personal_studio.feature.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.feature.knowledge.ui.components.KbEntryRow
import com.example.personal_studio.feature.knowledge.ui.components.KbSearchBar
import com.example.personal_studio.feature.knowledge.vm.KbHomeFilter
import com.example.personal_studio.feature.knowledge.vm.KbHomeUiState
import com.example.personal_studio.feature.knowledge.vm.KbHomeViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.TgChip
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.components.TgGroupLabel
import com.example.personal_studio.ui.theme.Tg

/**
 * 资料 → 知识库: search, one row of filters (kind first, then categories), and the entries.
 * Search runs full-text with the AND → OR rescue inside [KbHomeViewModel], so the list
 * below is always the one to show.
 */
@Composable
fun KbHomeScreen(
    onOpenEntry: (Long) -> Unit,
) {
    val vm: KbHomeViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "search") {
            KbSearchBar(
                query = state.searchQuery,
                onQueryChange = vm::onSearchChange,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp).enterUp(true, 0),
            )
        }
        item(key = "filters") {
            FilterRow(state, vm, Modifier.padding(top = 12.dp).enterUp(true, 60))
        }
        item(key = "label") {
            TgGroupLabel(
                when {
                    state.isSearching -> if (state.entries.isEmpty()) "没有找到" else "找到 ${state.entries.size} 条"
                    state.selectedCategoryId != null || state.filter != KbHomeFilter.ALL -> "${state.entries.size} 条"
                    else -> "最近"
                },
                modifier = Modifier.padding(horizontal = 20.dp).enterUp(true, 100),
            )
        }
        if (state.entries.isEmpty()) {
            item(key = "empty") {
                val (title, body) = emptyText(state)
                TgEmptyState(title, body, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).enterUp(true, 120))
            }
        }
        items(state.entries, key = { it.id }) { e ->
            KbEntryRow(entry = e, now = now, onClick = onOpenEntry, modifier = Modifier.animateItem().padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun FilterRow(state: KbHomeUiState, vm: KbHomeViewModel, modifier: Modifier) {
    val c = Tg.colors
    // The row runs to the screen edges so chips scroll out from under the margin.
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TgChip("全部", state.filter == KbHomeFilter.ALL, onClick = { vm.setFilter(KbHomeFilter.ALL) },
                count = state.notesCount + state.mistakesCount)
        }
        item {
            TgChip("笔记", state.filter == KbHomeFilter.NOTES_ONLY, count = state.notesCount, onClick = {
                vm.setFilter(if (state.filter == KbHomeFilter.NOTES_ONLY) KbHomeFilter.ALL else KbHomeFilter.NOTES_ONLY)
            })
        }
        item {
            TgChip("错题", state.filter == KbHomeFilter.MISTAKES_ONLY, count = state.mistakesCount, onClick = {
                vm.setFilter(if (state.filter == KbHomeFilter.MISTAKES_ONLY) KbHomeFilter.ALL else KbHomeFilter.MISTAKES_ONLY)
            })
        }
        val cats = state.categories.filter { it.count > 0 || it.category.id == state.selectedCategoryId }
        if (cats.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(c.line)) }
        }
        items(cats, key = { it.category.id }) { (cat, count) ->
            val selected = state.selectedCategoryId == cat.id
            TgChip(cat.name, selected, count = count, onClick = { vm.onSelectCategory(if (selected) null else cat.id) })
        }
    }
}

private fun emptyText(state: KbHomeUiState): Pair<String, String> = when {
    state.isSearching -> "没有相关条目" to "换个关键词试试，标题、题目和摘要都会被搜索。"
    state.notesCount + state.mistakesCount == 0 ->
        "知识库还是空的" to "在问答里把讲得好的回答存进来，或者把扫描的页面存成笔记；带题目的会归入错题。"
    state.filter == KbHomeFilter.MISTAKES_ONLY -> "这里还没有错题" to "拍题提问后把讲解存入知识库，就会出现在这里。"
    else -> "这个分类下还没有条目" to "换一个分类，或者回到“全部”。"
}
