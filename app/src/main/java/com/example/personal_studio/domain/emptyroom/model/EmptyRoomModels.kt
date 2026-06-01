package com.example.personal_studio.domain.emptyroom.model

/** 校区。 */
data class Campus(val code: String, val name: String)

/** 教学楼。 */
data class Building(val code: String, val name: String, val campusCode: String)

/** 单教室在某日的空闲情况。
 *  busyPeriods: 忙节次集(1..13);freeRanges: 合并后的连续空闲区间(闭区间)。 */
data class RoomFreeSlots(
    val roomName: String,
    val buildingName: String,
    val busyPeriods: Set<Int>,
    val freeRanges: List<IntRange>,
    val status: RoomStatus,
)

/** 相对「某一时刻」(默认现在)的状态。 */
data class RoomStatus(
    val freeNow: Boolean,
    /** freeNow=true 时:能连续空到的钟点(分钟数,从一天 0 点起);用于「还能空多久」。 */
    val freeUntilMinuteOfDay: Int?,
    /** freeNow=false 时:下一个变空的钟点(分钟);null=今天不再空。 */
    val nextFreeMinuteOfDay: Int?,
)

sealed interface EmptyRoomError {
    object NeedLogin : EmptyRoomError
    object WrongCredentials : EmptyRoomError
    data class Network(val cause: String) : EmptyRoomError
    data class Parse(val message: String) : EmptyRoomError
    data class Unexpected(val cause: String) : EmptyRoomError
}
