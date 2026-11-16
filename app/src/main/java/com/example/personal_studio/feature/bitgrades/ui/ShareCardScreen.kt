package com.example.personal_studio.feature.bitgrades.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.charts.GpaLineChart
import com.example.personal_studio.core.charts.GradeBarChart
import com.example.personal_studio.core.charts.LinePoint
import com.example.personal_studio.core.util.GradeBucketer
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.feature.bitgrades.ShareCardViewModel
import com.example.personal_studio.feature.timeline.today.enterUp
import com.example.personal_studio.ui.components.TgTopBar
import com.example.personal_studio.ui.components.TgCard
import com.example.personal_studio.ui.components.TgEmptyState
import com.example.personal_studio.ui.components.TgPillButton
import com.example.personal_studio.ui.components.TgSettingRow
import com.example.personal_studio.ui.components.TgSwitch
import com.example.personal_studio.ui.shell.LocalShellToast
import com.example.personal_studio.ui.theme.PaperCanvas
import com.example.personal_studio.ui.theme.Tg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 成绩分享卡: a preview of the card and 分享图片. The card always uses the 正午 paper palette
 * ([PaperCanvas]) so the image looks the same whatever the hour. Capture: the card's Box
 * records each frame into a GraphicsLayer and draws it back, so the shared PNG is always
 * the current frame (also after toggling the course list). The PNG goes to cacheDir/share/
 * and out through the FileProvider.
 */
@Composable
fun ShareCardScreen(
    onBack: () -> Unit,
    vm: ShareCardViewModel = hiltViewModel(),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    var includeCourses by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val toast = LocalShellToast.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TgTopBar(route = "分享成绩", subtitle = "分享成绩\n生成一张图片，可以发给别人或存到相册", onBack = onBack)
        if (book.isEmpty) {
            TgEmptyState("还没有成绩", "同步成绩后才能生成分享卡。", Modifier.padding(20.dp))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        ) {
            TgCard(Modifier.enterUp(true, 0)) {
                TgSettingRow("包含课程明细", summary = "每门课的学分、成绩和绩点都会出现在图片里", first = true,
                    trailing = { TgSwitch(includeCourses, { includeCourses = it }) })
            }
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .enterUp(true, 60)
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    },
            ) {
                PaperCanvas { ShareCard(book = book, includeCourses = includeCourses) }
            }
        }
        TgPillButton(
            if (sharing) "正在生成…" else "分享图片",
            icon = Icons.Outlined.IosShare,
            onClick = {
                if (sharing) return@TgPillButton
                sharing = true
                scope.launch {
                    try {
                        shareBitmap(context, graphicsLayer.toImageBitmap().asAndroidBitmap())
                    } catch (_: Throwable) {
                        toast?.show("没能生成图片，请再试一次")
                    } finally {
                        sharing = false
                    }
                }
            },
            enabled = !sharing,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun ShareCard(book: GradeBook, includeCourses: Boolean) {
    val c = Tg.colors
    val date = remember { SimpleDateFormat("yyyy 年 M 月 d 日", Locale.US).format(Date()) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.bg, shape)
            .border(1.dp, c.line, shape)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("成绩单", style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.weight(1f))
            Text(date, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Text("总绩点", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 20.dp))
        Text(String.format(Locale.US, "%.2f", book.overallGpa), style = MaterialTheme.typography.displayLarge, color = c.accent)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat("加权均分", book.overallAvgScore?.let { String.format(Locale.US, "%.1f", it) } ?: "—")
            Stat("学分", fmtCredits(book.totalCredits))
            Stat("课程", "${book.terms.sumOf { it.courses.size }} 门")
        }
        if (book.overallPeerAvgGpa != null || book.overallPeerAvgScore != null) {
            val parts = listOfNotNull(
                book.overallPeerAvgGpa?.let { "绩点 " + String.format(Locale.US, "%.2f", it) },
                book.overallPeerAvgScore?.let { "均分 " + String.format(Locale.US, "%.1f", it) },
            )
            Text("同课同学平均 · " + parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                modifier = Modifier.padding(top = 10.dp))
        }

        Section("各学期绩点") {
            GpaLineChart(book.terms.reversed().map { LinePoint(shortTermLabel(it.termName), it.weightedGpa) })
        }
        Section("成绩分布") {
            GradeBarChart(GradeBucketer.bucket(book.terms.flatMap { it.courses }))
        }

        if (includeCourses) {
            book.terms.forEach { t ->
                Section(fullTermName(t.termName) + " · GPA " + String.format(Locale.US, "%.2f", t.weightedGpa)) {
                    t.courses.forEach { g ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(g.courseName, style = MaterialTheme.typography.bodySmall, color = if (g.isPass) c.text else c.error,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(fmtCredits(g.credit), style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                                modifier = Modifier.width(40.dp))
                            Text(g.score, style = MaterialTheme.typography.bodySmall, color = if (g.isPass) c.text else c.error,
                                modifier = Modifier.width(44.dp))
                            Text(g.gradePoint?.let { String.format(Locale.US, "%.1f", it) } ?: "", style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary, modifier = Modifier.width(32.dp))
                        }
                    }
                }
            }
        }

        Box(Modifier.padding(top = 20.dp).fillMaxWidth().height(1.dp).background(c.line))
        Text("Personal Studio", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Tg.colors
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val c = Tg.colors
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = c.text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

/** Writes the PNG to cacheDir/share/ and offers it with ACTION_SEND through the FileProvider. */
private suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "transcript_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享成绩单").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
