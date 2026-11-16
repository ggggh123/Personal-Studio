package com.example.personal_studio.domain.chat

/** Names of chat sessions before the model has given them a real title. */
object ChatTitles {
    /** Default name of a new conversation. */
    const val NEW = "新对话"

    /** A placeholder (also the old "session #NNN" names) is replaced after the first answer. */
    fun isPlaceholder(title: String): Boolean = title.isBlank() || title == NEW || title.startsWith("session #")
}
