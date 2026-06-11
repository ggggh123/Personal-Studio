package com.example.personal_studio.feature.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.model.ChatSessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val sessions: List<ChatSessionSummary> = emptyList(),
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatListUiState> = repo.observeSessionSummaries()
        .map { ChatListUiState(sessions = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatListUiState())

    private var creating = false

    fun createNewSession(onCreated: (Long) -> Unit) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                val nextNumber = (repo.countSessions() + 1).toString().padStart(3, '0')
                val id = repo.createSession(initialTitle = "session #$nextNumber")
                onCreated(id)
            } finally {
                creating = false
            }
        }
    }

    fun onRename(id: Long, title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        viewModelScope.launch { repo.renameSession(id, t) }
    }

    fun onDelete(id: Long) = viewModelScope.launch { repo.deleteSession(id) }
}
