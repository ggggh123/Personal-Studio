package com.example.personal_studio.feature.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.model.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val sessions: List<ChatSession> = emptyList(),
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatListUiState> = repo.observeSessions()
        .map { ChatListUiState(sessions = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ChatListUiState(),
        )

    fun createNewSession(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val nextNumber = (repo.countSessions() + 1).toString().padStart(3, '0')
            val id = repo.createSession(initialTitle = "session #$nextNumber")
            onCreated(id)
        }
    }
}
