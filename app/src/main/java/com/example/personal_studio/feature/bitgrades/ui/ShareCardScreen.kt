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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.core.charts.GpaLineChart
import com.example.personal_studio.core.charts.GradeBarChart
import com.example.personal_studio.core.charts.LinePoint
import com.example.personal_studio.core.util.CreditFormat
import com.example.personal_studio.core.util.GradeBucketer
import com.example.personal_studio.domain.bitgrades.model.GradeBook
import com.example.personal_studio.feature.bitgrades.ShareCardViewModel
import com.example.personal_studio.ui.theme.Carmine
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Deep
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import com.example.personal_studio.ui.theme.Rule
import com.example.personal_studio.ui.theme.Void
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 成绩单分享卡片 (P6 M3)。
 *
 * 设计要点:
 * 1. 卡片本体 Composable 放在一个 Box 内,Box 用 drawWithContent + GraphicsLayer.record
 *    边渲染边把这一帧记录到 layer 里。所以即使用户切换 includeCourses 开关、
 *    导致卡片 recompose,下次 draw 时 layer 会被重新 record——按下分享时拿到的
 *    永远是当前一帧。
 * 2. graphicsLayer.toImageBitmap() 在 Compose 1.7 是 suspend → 必须放协程里。
 * 3. PNG 写到 cacheDir/share/,通过 FileProvider 暴露给 ACTION_SEND;
 *    file_paths.xml 已加 <cache-path name="share" path="share/"/>.
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
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    Column(
        Modifier
            .fillMaxSize()
            .background(Void)
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←", color = FoamMute) }
            Text("$ share", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "包含课程明细",
                color = FoamMute,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Switch(
                checked = includeCourses,
                onCheckedChange = { includeCourses = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Void,
                    checkedTrackColor = Phosphor,
                    uncheckedThumbColor = FoamMute,
                    uncheckedTrackColor = Deep,
                    uncheckedBorderColor = Rule,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))

        if (book.isEmpty) {
            Spacer(Modifier.height(48.dp))
            Text("还没有成绩数据,无法生成分享卡。", color = FoamMute)
            return@Column
        }

        // 卡片预览区:可竖向滚动(开「包含课程明细」时会很长);Share 按钮固定底部。
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // graphicsLayer 捕获区:Box 自身和子内容的渲染会写入 layer。
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Void)
                    .drawWithContent {
                        // 每帧把当前内容 record 到 layer,按下分享按钮时
                        // toImageBitmap() 拿到的就是最新一帧。
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        // 把 layer 再画回屏幕,否则用户看不到卡片。
                        drawLayer(graphicsLayer)
                    },
            ) {
                ShareCard(book = book, includeCourses = includeCourses)
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (sharing) return@Button
                sharing = true
                scope.launch {
                    try {
                        val img = graphicsLayer.toImageBitmap()
                        shareBitmap(context, img.asAndroidBitmap())
                    } catch (_: Throwable) {
                        // 截图/分享失败不致命,只复位按钮态。
                    } finally {
                        sharing = false
                    }
                }
            },
            enabled = !sharing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sharing) "导出中..." else "↗ 分享 PNG") }
    }
}

@Composable
private fun ShareCard(book: GradeBook, includeCourses: Boolean) {
    val date = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Void, RoundedCornerShape(8.dp))
            .border(1.dp, Rule, RoundedCornerShape(8.dp))
            .padding(20.dp),
    ) {
        // 顶部 prompt 头 + 日期
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ transcript", color = Phosphor, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(date, color = FoamDim, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(16.dp))

        // 三栏:总 GPA / 加权均分 / 总学分
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("总 GPA", String.format(Locale.US, "%.2f", book.overallGpa), Phosphor)
            Stat(
                "加权均分",
                book.overallAvgScore?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                Cyan,
            )
            Stat("总学分", CreditFormat.format(book.totalCredits), Phosphor)
        }

        // 估计班级平均(若有)
        if (book.overallPeerAvgGpa != null || book.overallPeerAvgScore != null) {
            Spacer(Modifier.height(12.dp))
            val parts = mutableListOf<String>()
            book.overallPeerAvgGpa?.let { parts += "GPA " + String.format(Locale.US, "%.2f", it) }
            book.overallPeerAvgScore?.let { parts += "均分 " + String.format(Locale.US, "%.1f", it) }
            Text(
                "估计班级平均: " + parts.joinToString(" · "),
                color = FoamMute,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(16.dp))

        // mini 趋势
        Column(
            Modifier
                .fillMaxWidth()
                .background(Deep, RoundedCornerShape(6.dp))
                .padding(12.dp),
        ) {
            Text("GPA 趋势", color = FoamMute, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            val points = book.terms.reversed().map { LinePoint(shortTermName(it.termName), it.weightedGpa) }
            GpaLineChart(points)
        }
        Spacer(Modifier.height(8.dp))

        // mini 分布
        Column(
            Modifier
                .fillMaxWidth()
                .background(Deep, RoundedCornerShape(6.dp))
                .padding(12.dp),
        ) {
            Text("成绩分布", color = FoamMute, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            val allCourses = book.terms.flatMap { it.courses }
            GradeBarChart(GradeBucketer.bucket(allCourses))
        }

        if (includeCourses) {
            Spacer(Modifier.height(16.dp))
            Text(
                "── 课程明细 ──",
                color = FoamDim,
                style = MaterialTheme.typography.labelMedium,
            )
            book.terms.forEach { t ->
                Spacer(Modifier.height(8.dp))
                val credits = t.courses.sumOf { it.credit }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.termName, color = Phosphor, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${t.courses.size}门·${CreditFormat.format(credits)}学分",
                        color = FoamMute,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "GPA " + String.format(Locale.US, "%.2f", t.weightedGpa),
                        color = FoamMute,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                t.courses.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.courseName,
                            color = if (!c.isPass) Carmine else Foam,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            CreditFormat.format(c.credit),
                            color = FoamMute,
                            modifier = Modifier.width(44.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            c.score,
                            color = if (!c.isPass) Carmine else Foam,
                            modifier = Modifier.width(48.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        c.gradePoint?.let {
                            Text(
                                String.format(Locale.US, "%.2f", it),
                                color = FoamMute,
                                modifier = Modifier.width(40.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // 水印
        Text(
            "Personal-Studio · 终端风学习 App",
            color = FoamDim,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column {
        Text(value, color = valueColor, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = FoamMute, style = MaterialTheme.typography.labelMedium)
    }
}

/** 学期短名,沿用 GradesScreen.shortTerm 的规则。 */
private fun shortTermName(name: String): String {
    val m = Regex("""(\d{4})-(\d{4})-(\d)""").find(name)
    if (m != null) {
        val (y1, y2, sem) = m.destructured
        return when (sem) {
            "1" -> "${y1.takeLast(2)}秋"
            "2" -> "${y2.takeLast(2)}春"
            "3" -> "${y2.takeLast(2)}夏"
            else -> "${y1.takeLast(2)}-${y2.takeLast(2)}"
        }
    }
    return name.replace("学年", "").replace("第一学期", "上").replace("第二学期", "下")
        .replace(" ", "").take(6)
}

/** 把 bitmap 写到 cacheDir/share/ 并通过 FileProvider 发 ACTION_SEND。 */
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
