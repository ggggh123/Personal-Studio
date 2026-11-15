package com.example.personal_studio.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * The Terminal theme's CRT effects are retired. These stay as no-ops during the migration
 * (迁移方式 A) so screens that still call them compile unchanged; delete with the last caller.
 */

@Suppress("UNUSED_PARAMETER")
fun Modifier.scanLines(spacingDp: Float = 3f, alpha: Float = 0.025f): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.vignette(cornerDim: Float = 0.55f, centerGlow: Float = 0.05f): Modifier = this

@Composable
fun TerminalBackdrop(content: @Composable () -> Unit) = content()
