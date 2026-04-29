package com.example.personal_studio.data.local.db

import androidx.room.TypeConverter
import com.example.personal_studio.data.local.db.entity.KbSourceType
import com.example.personal_studio.data.local.db.entity.MessageRole

class Converters {
    @TypeConverter fun roleToString(role: MessageRole): String = role.name
    @TypeConverter fun stringToRole(value: String): MessageRole = MessageRole.valueOf(value)

    @TypeConverter fun kbSourceToString(value: KbSourceType): String = value.name
    @TypeConverter fun stringToKbSource(value: String): KbSourceType = KbSourceType.valueOf(value)
}
