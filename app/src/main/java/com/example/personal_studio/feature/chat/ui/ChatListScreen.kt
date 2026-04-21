package com.example.personal_studio.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personal_studio.domain.model.ChatSession
import com.example.personal_studio.feature.chat.vm.ChatListViewModel
import com.example.personal_studio.ui.theme.Amber
import com.example.personal_studio.ui.theme.Cyan
import com.example.personal_studio.ui.theme.Foam
import com.example.personal_studio.ui.theme.FoamDim
import com.example.personal_studio.ui.theme.FoamMute
import com.example.personal_studio.ui.theme.Phosphor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
    onOpenSession: (Long) -> Unit,
    vm: ChatListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // Prompt line header
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Amber)) { append("user@study") }
                withStyle(SpanStyle(color = FoamDim)) { append(":~$ ") }
                withStyle(SpanStyle(color = Foam)) { append("ls sessions/") }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "total ${state.sessions.size}",
            style = MaterialTheme.typography.bodySmall,
            color = FoamMute,
        )
        Spacer(Modifier.height(14.dp))

        if (state.sessions.isEmpty()) {
            EmptyState(onOpenNew = { vm.createNewSession(onCreated = onOpenSession) })
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(session, onClick = { onOpenSession(session.id) })
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                    withStyle(SpanStyle(color = FoamDim)) { append("tap ") }
                    withStyle(SpanStyle(color = Cyan)) { append("[new]") }
                    withStyle(SpanStyle(color = FoamDim)) { append(" to start a session") }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    vm.createNewSession(onCreated = onOpenSession)
                },
            )
        }
    }
}

@Composable
private fun SessionRow(session: ChatSession, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = fmt.format(Date(session.updatedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = FoamMute,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = session.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Foam,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyState(onOpenNew: () -> Unit) {
    Column {
        Text("no sessions yet", style = MaterialTheme.typography.bodyMedium, color = FoamMute)
        Spacer(Modifier.height(24.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Phosphor)) { append("▓ ") }
                withStyle(SpanStyle(color = Cyan)) { append("[new]") }
                withStyle(SpanStyle(color = FoamDim)) { append(" — tap here to start your first session") }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClick = onOpenNew),
        )
    }
}
