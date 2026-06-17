package com.example.personal_studio.feature.chat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personal_studio.BuildConfig
import com.example.personal_studio.data.local.datastore.UserPreferencesRepository
import com.example.personal_studio.data.repository.ChatRepository
import com.example.personal_studio.domain.chat.GenerateTitleUseCase
import com.example.personal_studio.domain.chat.SendChunk
import com.example.personal_studio.domain.chat.SendMessageUseCase
import com.example.personal_studio.domain.model.ChatMessage
import com.example.personal_studio.domain.model.ChatSession
import com.example.personal_studio.domain.model.MessageRole
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class ChatDetailUiState(
    val session: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val attachedImagePaths: List<String> = emptyList(),
    val streamingText: String? = null,
    val errorBanner: String? = null,
    val isSending: Boolean = false,
    /** Active model id from preferences (or the bundled default). Surfaced in the top bar. */
    val activeModel: String = BuildConfig.DEFAULT_LLM_MODEL,
    /** Incremented each time a send round-trip (including title generation) fully completes. */
    val sendGeneration: Int = 0,
)

@HiltViewModel(assistedFactory = ChatDetailViewModel.Factory::class)
class ChatDetailViewModel @AssistedInject constructor(
    @Assisted val sessionId: Long,
    private val repo: ChatRepository,
    private val send: SendMessageUseCase,
    private val titleGen: GenerateTitleUseCase,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: Long): ChatDetailViewModel
    }

    companion object { const val MAX_IMAGES = 6 }

    private val _uiState = MutableStateFlow(ChatDetailUiState())
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    init {
        repo.observeMessages(sessionId)
            // SYSTEM messages (e.g. the grade-context seeded by StartGradeChatUseCase)
            // are part of the LLM context but must not render as chat bubbles.
            .onEach { msgs ->
                _uiState.update {
                    it.copy(messages = msgs.filter { m ->
                        m.role != com.example.personal_studio.domain.model.MessageRole.SYSTEM
                    })
                }
            }
            .launchIn(viewModelScope)

        prefs.modelName
            .onEach { name ->
                _uiState.update {
                    it.copy(activeModel = name?.takeIf { v -> v.isNotBlank() } ?: BuildConfig.DEFAULT_LLM_MODEL)
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            repo.getSession(sessionId)?.let { s ->
                _uiState.update { it.copy(session = s) }
            }
        }
    }

    fun onInputChanged(value: String) { _uiState.update { it.copy(input = value) } }

    /** 追加一张图(已满 MAX_IMAGES 则忽略)。裁剪确认后调用。 */
    fun onAttachImage(path: String) = _uiState.update {
        if (it.attachedImagePaths.size >= MAX_IMAGES) it
        else it.copy(attachedImagePaths = it.attachedImagePaths + path)
    }

    /** 从预览里移除指定图。 */
    fun onRemoveAttachedImage(path: String) = _uiState.update {
        it.copy(attachedImagePaths = it.attachedImagePaths - path)
    }

    fun onDismissError() { _uiState.update { it.copy(errorBanner = null) } }

    private var sendJob: Job? = null

    fun onSend() {
        if (_uiState.value.isSending) return
        val text = _uiState.value.input.trim()
        val imagePaths = _uiState.value.attachedImagePaths
        if (text.isBlank() && imagePaths.isEmpty()) return

        _uiState.update {
            it.copy(
                input = "",
                attachedImagePaths = emptyList(),
                isSending = true,
                streamingText = "",
            )
        }

        sendJob = viewModelScope.launch {
            val buffer = StringBuilder()
            send(sessionId = sessionId, userText = text, userImagePaths = imagePaths).collect { chunk ->
                when (chunk) {
                    is SendChunk.UserPersisted -> { /* messages flow updates the list */ }
                    is SendChunk.Delta -> {
                        buffer.append(chunk.text)
                        _uiState.update { it.copy(streamingText = buffer.toString()) }
                    }
                    is SendChunk.AiPersisted -> {
                        _uiState.update {
                            it.copy(streamingText = null, isSending = false)
                        }
                        yield() // allow collectors to observe the persisted state before continuing
                        maybeGenerateTitle()
                        // Bump generation to guarantee one stable "fully done" emission after all
                        // title work; this also ensures Turbine-based tests can call
                        // expectMostRecentItem() after the streaming loop exits.
                        _uiState.update { it.copy(sendGeneration = it.sendGeneration + 1) }
                    }
                    is SendChunk.Error -> {
                        _uiState.update {
                            it.copy(streamingText = null, isSending = false, errorBanner = chunk.message)
                        }
                    }
                }
            }
        }
    }

    /** 用户「⏹ 停止」:取消正在生成的回复,保留已流式出的半截内容(存为 AI 消息 + 「（已停止）」)。 */
    fun onStop() {
        sendJob?.cancel()
        sendJob = null
        val partial = _uiState.value.streamingText
        _uiState.update { it.copy(streamingText = null, isSending = false) }
        if (!partial.isNullOrBlank()) {
            viewModelScope.launch {
                repo.appendMessage(
                    sessionId = sessionId,
                    role = MessageRole.AI,
                    content = partial.trimEnd() + "\n\n（已停止）",
                    modelUsed = _uiState.value.activeModel,
                )
            }
        }
    }

    private suspend fun maybeGenerateTitle() {
        val session = _uiState.value.session ?: return
        val isStubTitle = session.title.startsWith("session #")
        val userMsgs = _uiState.value.messages.count {
            it.role == com.example.personal_studio.domain.model.MessageRole.USER
        }
        if (isStubTitle && userMsgs >= 2) {
            val newTitle = titleGen(sessionId) ?: return
            _uiState.update { it.copy(session = session.copy(title = newTitle)) }
        }
    }
}
