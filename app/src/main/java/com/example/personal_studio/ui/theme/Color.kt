package com.example.personal_studio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/*
 * Transitional colour names (迁移方式 A, see docs/ui-redesign.md).
 *
 * These names come from the retired Terminal theme and are still referenced across the
 * screens that have not been redesigned yet. Instead of fixed values they now read the
 * current 天光 tokens, so every old screen follows the daylight palette immediately.
 * A redesigned screen must use [Tg] tokens directly; once nothing references these
 * names any more, delete this file.
 */

/** Page background. */
val Void: Color @Composable @ReadOnlyComposable get() = Tg.colors.bg

/** Card surface. */
val Deep: Color @Composable @ReadOnlyComposable get() = Tg.colors.surface

/** Raised surface: sheets, dialogs, menus. */
val Hull: Color @Composable @ReadOnlyComposable get() = Tg.colors.surfaceRaised

/** Hairlines and borders. */
val Rule: Color @Composable @ReadOnlyComposable get() = Tg.colors.line

/** Former secondary border; merged into the hairline colour. */
val Dim: Color @Composable @ReadOnlyComposable get() = Tg.colors.line

/** Body text. */
val Foam: Color @Composable @ReadOnlyComposable get() = Tg.colors.text

/** Supporting text. */
val FoamMute: Color @Composable @ReadOnlyComposable get() = Tg.colors.textSecondary

/** Former hint text; kept readable as supporting text. */
val FoamDim: Color @Composable @ReadOnlyComposable get() = Tg.colors.textSecondary

/** Former primary green; now the time-of-day accent. */
val Phosphor: Color @Composable @ReadOnlyComposable get() = Tg.colors.accent

/** Former warning amber; merged into the accent. */
val Amber: Color @Composable @ReadOnlyComposable get() = Tg.colors.accent

/** Former info cyan; merged into supporting text. */
val Cyan: Color @Composable @ReadOnlyComposable get() = Tg.colors.textSecondary

/** Errors. */
val Carmine: Color @Composable @ReadOnlyComposable get() = Tg.colors.error

/** Former "done" olive; merged into supporting text. */
val Olive: Color @Composable @ReadOnlyComposable get() = Tg.colors.textSecondary
