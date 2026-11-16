package com.example.personal_studio.feature.timeline.ui.components

import androidx.compose.runtime.Composable
import com.example.personal_studio.core.util.SemesterTimeMapper
import com.example.personal_studio.ui.components.TgDatePickerDialog
import java.time.LocalDate

/**
 * Pick the first week of the semester (any day of it). [onPicked] receives the Monday of
 * that week, which is what course weeks count from. Dismissing calls [onDismiss].
 */
@Composable
fun SemesterStartModal(
    initial: LocalDate = LocalDate.now(),
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    TgDatePickerDialog(
        title = "学期第一周",
        hint = "选第一周里的任意一天，会按那周的周一计算",
        initial = initial,
        onPick = { onPicked(SemesterTimeMapper.normalizeSemesterStart(it)) },
        onDismiss = onDismiss,
    )
}
