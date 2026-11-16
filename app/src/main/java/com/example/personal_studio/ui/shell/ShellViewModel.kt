package com.example.personal_studio.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Actions of the app shell that need data: today only "＋ → 拍题提问". */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private var creating = false

    /** Creates an empty chat session ("新对话", titled after the first answer) and reports its id. */
    fun newChatSession(onCreated: (Long) -> Unit) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            try {
                onCreated(chatRepository.createSession(initialTitle = com.example.personal_studio.domain.chat.ChatTitles.NEW))
            } finally {
                creating = false
            }
        }
    }
}
