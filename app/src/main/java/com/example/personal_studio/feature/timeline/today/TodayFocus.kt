package com.example.personal_studio.feature.timeline.today

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands an item to the 今日 page from elsewhere in the shell (the quick "新建待办" sheet):
 * 今日 jumps to the item's day and briefly highlights its row.
 */
@Singleton
class TodayFocus @Inject constructor() {
    private val _requests = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)
    val requests: SharedFlow<Long> = _requests.asSharedFlow()

    fun focus(itemId: Long) {
        _requests.tryEmit(itemId)
    }

    /** Called once the request has been shown, so it is not replayed on the next visit. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumed() = _requests.resetReplayCache()
}
