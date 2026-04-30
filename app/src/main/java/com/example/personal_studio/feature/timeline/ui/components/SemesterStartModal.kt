package com.example.personal_studio.feature.timeline.ui.components

import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.personal_studio.core.util.SemesterTimeMapper
import java.time.LocalDate

/**
 * Native Android DatePickerDialog wrapper that ALWAYS normalises the picked date back to
 * the Monday of its ISO week before calling [onPicked]. This is shown before the user
 * can interact with AddCourseScreen if no semester start is configured yet.
 */
@Composable
fun SemesterStartModal(
    initial: LocalDate = LocalDate.now(),
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        DatePickerDialog(ctx, { _, y, m, d ->
            val picked = LocalDate.of(y, m + 1, d)
            onPicked(SemesterTimeMapper.normalizeSemesterStart(picked))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
            .apply { setOnCancelListener { onDismiss() } }
            .show()
    }
}
