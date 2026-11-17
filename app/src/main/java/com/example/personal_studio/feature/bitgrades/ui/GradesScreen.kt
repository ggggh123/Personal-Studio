package com.example.personal_studio.feature.bitgrades.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.bitgrades.model.GradeItem
import com.example.personal_studio.domain.bitgrades.model.TermGrades
import com.example.personal_studio.feature.bitgrades.GradesUiState
import com.example.personal_studio.feature.bitgrades.GradesViewModel
import com.example.personal_studio.feature.bitgrades.ui.components.fmtScore
import com.example.personal_studio.feature.bitgrades.ui.components.parseTopPercent
import com.example.personal_studio.feature.timeline.today.DetailAction
import com.example.personal_studio.feature.timeline.today.PillButton
import com.example.personal_studio.feature.timeline.today.TodayText
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.theme.SerifFamily
import com.example.personal_studio.ui.components.SpinningSyncIcon
import com.example.personal_studio.ui.theme.Tg
import com.example.personal_studio.ui.theme.TgMotion
import java.util.Locale

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * 成绩: the weighted GPA counting up, a bar per term, the courses term by term (tap to see
 * class / major position), and the local rank estimate with its caveat. "选择课程" lets the
 * user leave courses out of the GPA, as before.
 */
@Composable
fun GradesScreen(
    onBack: () -> Unit,
    onNeedLogin: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenWhatIf: () -> Unit,
    onOpenShare: () -> Unit,
    vm: GradesViewModel = hiltViewModel(),
) {
    val st by vm.uiState.collectAsStateWithLifecycle()
    val c = Tg.colors
    var showSheet by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val sync = { if (st.loggedIn) vm.onSyncNow() else onNeedLogin() }

    // The entrance stagger plays once; term cards scrolled into view later just appear.
    var introDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(st.loaded) {
        if (st.loaded) { delay(900); introDone = true }
    }
    val onToggleCourse = remember(vm) { { id: Long -> vm.onToggleCourse(id) } }
    val onToggleTerm = remember(vm) { { ids: List<Long> -> vm.onToggleTerm(ids) } }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(st, onBack, sync)
        StatusLine(st)
        // Until the database answers there is nothing to draw (not even the empty state), so
        // the page does not flash "还没有成绩" during the slide-in.
        if (!st.loaded) return@Column
        if (st.book.isEmpty) {
            EmptyGrades(st, sync)
            return@Column
        }
        // Lazy: opening the page composes only what is on screen (a long record has 60+
        // courses), and switching 选择课程 only touches the visible term cards.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "hero") { Hero(st, Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp).enterUp(!introDone, 50)) }
            item(key = "bars") { TermBars(st.book.terms, Modifier.padding(top = 22.dp)) }
            item(key = "actions") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionChip("目标绩点", onClick = onOpenWhatIf)
                    ActionChip("成绩分享卡", onClick = onOpenShare)
                    ActionChip("AI 学情报告") { showSheet = true; vm.onAnalyze() }
                    ActionChip(if (selecting) "完成选择" else "选择课程", active = selecting) {
                        selecting = !selecting
                    }
                }
            }
            item(key = "selectNote") {
                AnimatedVisibility(visible = selecting || st.filtering) {
                    Row(Modifier.padding(start = 24.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selecting) "点课程把它移出或放回计算。" else "已移出 ${st.excludedIds.size} 门课程。",
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f),
                        )
                        if (st.filtering) {
                            Text("全部计入", style = MaterialTheme.typography.labelLarge, color = c.accent,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = vm::onClearSelection).padding(8.dp))
                        }
                    }
                }
            }
            st.book.terms.forEachIndexed { ti, term ->
                val intro = !introDone && ti < 3
                item(key = "term-" + term.termCode) {
                    TermHeader(
                        term = term,
                        selecting = selecting,
                        excluded = st.excludedIds,
                        onToggleTerm = onToggleTerm,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp).enterUp(intro, 260 + ti * 40),
                    )
                }
                // One item per course, so only the rows on screen are composed and measured.
                itemsIndexed(term.courses, key = { _, course -> "course-" + course.id }) { i, course ->
                    CourseRow(
                        course = course,
                        first = i == 0,
                        last = i == term.courses.lastIndex,
                        selecting = selecting,
                        included = course.id !in st.excludedIds,
                        expanded = expandedId == course.id && !selecting,
                        onClick = {
                            if (selecting) onToggleCourse(course.id)
                            else expandedId = if (expandedId == course.id) null else course.id
                        },
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = if (i == 0) 10.dp else 0.dp)
                            .enterUp(intro, 260 + ti * 40),
                    )
                }
            }
            item(key = "rule") {
                Text(
                    "绩点按学校规则换算：百分制 60 分以下记 0，其余为 4 − 3 × (100 − 分数)² ÷ 1600；等级制按学校对照取值；通过制课程不计入。再按学分加权。",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    modifier = Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 22.dp),
                )
            }
        }
    }

    if (showSheet) {
        AiAnalysisSheet(
            text = st.analysis, analyzing = st.analyzing, error = st.analysisError,
            generatedAt = st.analysisAt, fromCache = st.analysisFromCache,
            onAskInChat = { vm.onAskInChat { sid -> showSheet = false; onOpenChat(sid) } },
            onRegenerate = vm::onRegenerate,
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun Header(st: GradesUiState, onBack: () -> Unit, onSync: () -> Unit) {
    val c = Tg.colors
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = c.text)
        }
        Spacer(Modifier.weight(1f))
        val label = when {
            st.syncing -> "同步中…"
            st.fetchedAt != null -> "同步于 " + TodayText.ago(st.fetchedAt, System.currentTimeMillis())
            else -> "同步成绩"
        }
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = !st.syncing, role = Role.Button, onClickLabel = "同步成绩", onClick = onSync)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpinningSyncIcon(st.syncing, c.textSecondary, Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun StatusLine(st: GradesUiState) {
    val c = Tg.colors
    val text = when {
        st.syncing -> st.syncSteps.lastOrNull()
        st.syncError != null -> st.syncError.userMessage()
        else -> null
    }
    AnimatedVisibility(visible = text != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(
            text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (st.syncError != null && !st.syncing) c.error else c.accent,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyGrades(st: GradesUiState, onSync: () -> Unit) {
    val c = Tg.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp)) {
        Text("还没有成绩", style = MaterialTheme.typography.headlineMedium, color = c.text)
        Text(
            if (st.loggedIn) "从教务系统读取全部学期的成绩，只保存在本机。" else "登录学校账户后，可以从教务系统读取成绩。",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        PillButton(DetailAction(if (st.loggedIn) "同步成绩" else "去登录", primary = true, onClick = onSync), Modifier.widthIn(min = 160.dp))
    }
}

@Composable
private fun Hero(st: GradesUiState, modifier: Modifier = Modifier) {
    val c = Tg.colors
    var rankOpen by rememberSaveable { mutableStateOf(false) }
    val gpa = if (st.filtering) st.selectedGpa else st.book.overallGpa
    val credits = if (st.filtering) st.selectedCredits else st.book.totalCredits
    val avg = if (st.filtering) st.selectedAvgScore else st.book.overallAvgScore
    val rank = if (st.filtering) st.selectedMajorRankEst else st.book.overallMajorRankEst
    val peer = if (st.filtering) st.selectedPeerAvgGpa else st.book.overallPeerAvgGpa
    val totals = remember(st.book) {
        val all = st.book.terms.flatMap { it.courses }
        all.size to all.count { it.gradePoint == null }
    }
    val counted = if (st.filtering) st.selectedCount else totals.first
    val noGpa = totals.second

    Column(modifier) {
        Text(
            if (st.filtering) "选中 $counted 门 · 按学分加权" else "总绩点 · 按学分加权",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = c.textSecondary,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            CountingGpa(gpa, Modifier.semantics { contentDescription = "总绩点 " + String.format(Locale.US, "%.2f", gpa) })
            Text("/ 4.00", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            listOfNotNull(
                "$counted 门课程",
                "${fmtCredits(credits)} 学分",
                avg?.let { "均分 ${fmtScore(it)}" },
                noGpa.takeIf { it > 0 && !st.filtering }?.let { "$it 门不计绩点" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
            color = c.textSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (rank != null) {
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accentSoft)
                    .clickable(onClickLabel = if (rankOpen) "收起说明" else "查看说明") { rankOpen = !rankOpen }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics { stateDescription = if (rankOpen) "已展开" else "已收起" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("专业约前 ${pct(rank.pointPercent)}% · 本地估计", style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = c.accent)
                Icon(Icons.Outlined.Info, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
            }
            AnimatedVisibility(rankOpen, enter = expandVertically(tween(300)) + fadeIn(tween(300)), exit = shrinkVertically(tween(220)) + fadeOut(tween(160))) {
                Text(
                    "根据 ${rank.basisCount} 门课各自在专业中的位置按学分估算，参考区间约前 ${pct(rank.loPercent)}%–${pct(rank.hiPercent)}%" +
                        (peer?.let { "；同课同学的平均绩点约 " + String.format(Locale.US, "%.2f", it) } ?: "") +
                        "。这是本机的估计，不是学校公布的排名。",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * The GPA counting up from zero the first time, then gliding between values when the
 * selection changes. Only this composable recomposes per animation frame.
 */
@Composable
private fun CountingGpa(gpa: Double, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(gpa) {
        shown.animateTo(gpa.toFloat(), tween(if (first) 1200 else 500, easing = EaseOutCubic))
        first = false
    }
    StableDigits(String.format(Locale.US, "%.2f", shown.value), MaterialTheme.typography.displayLarge, Tg.colors.text, modifier)
}

/** Digits in equal-width cells: the serif face has no tabular figures, and a counting number must not jitter. */
@Composable
private fun StableDigits(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cell = remember(style, density) {
        with(density) { (0..9).maxOf { measurer.measure(it.toString(), style).size.width }.toDp() }
    }
    Row(modifier) {
        text.forEach { ch ->
            if (ch.isDigit()) {
                Box(Modifier.width(cell), contentAlignment = Alignment.Center) { Text(ch.toString(), style = style, color = color) }
            } else {
                Text(ch.toString(), style = style, color = color)
            }
        }
    }
}

/** One bar per term, oldest on the left; the newest is in the accent. Bars grow in turn. */
@Composable
private fun TermBars(terms: List<TermGrades>, modifier: Modifier = Modifier) {
    val c = Tg.colors
    val ordered = terms.reversed()
    if (ordered.isEmpty()) return
    Row(
        modifier.fillMaxWidth().height(156.dp).padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(if (ordered.size > 5) 10.dp else 18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ordered.forEachIndexed { i, term ->
            val target = barHeight(term.weightedGpa)
            val grow = remember { Animatable(0f) }
            LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, delayMillis = 200 + i * 120, easing = EaseOutCubic)) }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(
                    String.format(Locale.US, "%.2f", term.weightedGpa),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = c.text,
                    modifier = Modifier.graphicsLayer { alpha = grow.value },
                )
                Box(
                    Modifier
                        .padding(top = 6.dp, bottom = 6.dp)
                        .widthIn(max = 60.dp)
                        .fillMaxWidth()
                        .height(target.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = grow.value
                        }
                        .background(
                            if (i == ordered.lastIndex) c.accent else c.textTertiary,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        ),
                )
                Text(shortTermLabel(term.termName), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = Tg.colors
    val shape = RoundedCornerShape(20.dp)
    val bg by animateColorAsState(if (active) c.text else Color.Transparent, tween(200), label = "chipBg")
    val fg by animateColorAsState(if (active) c.bg else c.text, tween(200), label = "chipFg")
    Box(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (active) c.text else c.textTertiary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize), color = fg)
    }
}

@Composable
private fun TermHeader(
    term: TermGrades,
    selecting: Boolean,
    excluded: Set<Long>,
    onToggleTerm: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val credits = remember(term) { term.courses.sumOf { it.credit } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(fullTermName(term.termName), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        if (selecting) {
            val allIn = term.courses.none { it.id in excluded }
            Text(if (allIn) "全不选" else "全选", style = MaterialTheme.typography.labelLarge, color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onToggleTerm(term.courses.map { it.id }) }.padding(horizontal = 8.dp, vertical = 10.dp))
        } else {
            Text("${fmtCredits(credits)} 学分 · " + String.format(Locale.US, "%.2f", term.weightedGpa),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
        }
    }
}

/**
 * This row's slice of the term card: page surface, rounded top corners on the first row and
 * bottom corners on the last, and the side borders, with the top / bottom edge only where the
 * card begins or ends (rows between are divided by their own separator line).
 */
private fun Modifier.cardSegment(first: Boolean, last: Boolean, fill: Color, line: Color): Modifier {
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    return this
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            val w = 1.dp.toPx()
            val radius = androidx.compose.ui.geometry.CornerRadius(r.toPx())
            val zero = androidx.compose.ui.geometry.CornerRadius.Zero
            val outline = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        0f, 0f, size.width, size.height,
                        topLeftCornerRadius = if (first) radius else zero, topRightCornerRadius = if (first) radius else zero,
                        bottomRightCornerRadius = if (last) radius else zero, bottomLeftCornerRadius = if (last) radius else zero,
                    ),
                )
            }
            // The stroke is centred on the edge and the outer half is clipped away by the shape.
            clipRect(top = if (first) 0f else w, bottom = if (last) size.height else size.height - w) {
                drawPath(outline, line, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2 * w))
            }
        }
}

@Composable
private fun CourseRow(
    course: GradeItem,
    first: Boolean,
    last: Boolean,
    selecting: Boolean,
    included: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Tg.colors
    val failed = !course.isPass
    val dim = animateFloatAsState(if (selecting && !included) 0.45f else 1f, tween(200), label = "dim")
    // 0 → 1 as selection mode opens. Read only in graphicsLayer, so the animation never
    // re-measures the row: the check slot is added in one step and the text slides by drawing.
    val sel = animateFloatAsState(if (selecting) 1f else 0f, TgMotion.standard(), label = "selSlide")
    val slot = with(LocalDensity.current) { 34.dp.toPx() }
    Column(
        modifier
            .fillMaxWidth()
            .cardSegment(first, last, c.surface, c.line)
            .drawBehind { if (!first) drawLine(c.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(
                    role = if (selecting) Role.Checkbox else Role.Button,
                    onClickLabel = if (selecting) null else if (expanded) "收起" else "展开成绩分析",
                    onClick = onClick,
                )
                .semantics { if (selecting) stateDescription = if (included) "计入绩点" else "不计入" }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selecting) {
                val fill by animateColorAsState(if (included) c.text else Color.Transparent, tween(200), label = "selFill")
                Box(
                    Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            alpha = sel.value
                            scaleX = 0.6f + 0.4f * sel.value
                            scaleY = 0.6f + 0.4f * sel.value
                        }
                        .background(fill, CircleShape)
                        .border(1.5.dp, if (included) c.text else c.textSecondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (included) Icon(Icons.Rounded.Check, contentDescription = null, tint = c.bg, modifier = Modifier.size(14.dp))
                }
            }
            Column(
                Modifier.weight(1f).graphicsLayer {
                    alpha = dim.value
                    // Entering: starts where it was (one slot to the left) and slides into place;
                    // leaving: starts one slot to the right and slides back.
                    translationX = if (selecting) -(1f - sel.value) * slot else sel.value * slot
                },
            ) {
                Text(course.courseName, style = MaterialTheme.typography.bodyLarge, color = c.text)
                Text(
                    listOfNotNull(
                        "${fmtCredits(course.credit)} 学分",
                        course.category?.takeIf { it.isNotBlank() },
                        course.attemptType.takeIf { it.isNotBlank() && it != "正常" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 1.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.graphicsLayer { alpha = dim.value }) {
                Text(
                    course.score,
                    style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
                    color = if (failed) c.error else c.text,
                )
                Text(
                    when {
                        failed -> "未通过"
                        course.gradePoint != null -> "绩点 " + String.format(Locale.US, "%.2f", course.gradePoint)
                        else -> "不计绩点"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = if (failed) c.error else c.textSecondary,
                )
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically(TgMotion.standard()) + fadeIn(tween(240)), exit = shrinkVertically(tween(260)) + fadeOut(tween(160))) {
            CourseAnalysis(course)
        }
    }
}

@Composable
private fun CourseAnalysis(course: GradeItem) {
    val c = Tg.colors
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        val tiles = listOfNotNull(
            course.courseAvg?.let { "课程均分" to fmtScore(it) },
            course.courseMaxScore?.let { "最高分" to fmtScore(it) },
            course.courseStudyCount?.let { "学习人数" to "$it" },
        )
        if (tiles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { (k, v) ->
                    Column(Modifier.weight(1f).background(c.bg, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(k, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), color = c.textSecondary)
                        Text(v, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal), color = c.text, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        val bars = listOfNotNull(
            parseTopPercent(course.classRankText)?.let { Triple("班级", it, c.text) },
            parseTopPercent(course.majorRankText)?.let { Triple("专业", it, c.accent) },
            parseTopPercent(course.schoolRankText)?.let { Triple("全校", it, c.textSecondary) },
        )
        bars.forEachIndexed { i, (label, p, color) -> PositionBar(label, p, color, i) }
        if (tiles.isEmpty() && bars.isEmpty()) {
            Text(
                if (course.gradePoint == null && course.isPass) "通过制课程，不计入绩点。" else "学校没有提供这门课的成绩分析。",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
        }
    }
}

/** "前 p%": the bar fills 100 − p, so further ahead reads as fuller. */
@Composable
private fun PositionBar(label: String, topPercent: Int, color: Color, index: Int) {
    val c = Tg.colors
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo((100 - topPercent) / 100f, tween(700, delayMillis = 120 + index * 80, easing = EaseOutCubic)) }
    Row(Modifier.padding(top = if (index == 0) 12.dp else 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.width(30.dp))
        Canvas(Modifier.weight(1f).height(6.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(c.line, cornerRadius = r)
            drawRoundRect(color, size = size.copy(width = size.width * fill.value), cornerRadius = r)
        }
        Text("前 $topPercent%", style = MaterialTheme.typography.bodySmall, color = c.text, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
    }
}

/** Bar height in dp for a term GPA: low terms still show a stub, 4.0 is the tallest. */
internal fun barHeight(gpa: Double): Float = (20 + (gpa - 3) * 110).toFloat().coerceIn(12f, 130f)

internal fun fmtCredits(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

private fun pct(v: Double): String = if (v < 10) String.format(Locale.US, "%.1f", v).removeSuffix(".0") else v.toInt().toString()

/** "2025-2026-1" → "2025–2026 学年 第一学期"; names already in words are kept. */
internal fun fullTermName(name: String): String {
    val m = Regex("""^(\d{4})-(\d{4})-(\d)$""").find(name.trim()) ?: return name
    val (y1, y2, sem) = m.destructured
    val n = when (sem) { "1" -> "一"; "2" -> "二"; "3" -> "三"; else -> sem }
    return "$y1–$y2 学年 第${n}学期"
}

/** "2025-2026-1" → "25 秋"; "2025–2026 学年 第二学期" → "26 春". */
internal fun shortTermLabel(name: String): String {
    Regex("""(\d{4})-(\d{4})-(\d)""").find(name)?.destructured?.let { (y1, y2, sem) ->
        return when (sem) {
            "1" -> "${y1.takeLast(2)} 秋"
            "2" -> "${y2.takeLast(2)} 春"
            "3" -> "${y2.takeLast(2)} 夏"
            else -> "${y1.takeLast(2)}-${y2.takeLast(2)}"
        }
    }
    Regex("""(\d{4})\D+(\d{4})\D*学年\D*第([一二三])学期""").find(name)?.destructured?.let { (y1, y2, n) ->
        return when (n) {
            "一" -> "${y1.takeLast(2)} 秋"
            "二" -> "${y2.takeLast(2)} 春"
            else -> "${y2.takeLast(2)} 夏"
        }
    }
    return name.replace("学年", "").replace(" ", "").take(6)
}
