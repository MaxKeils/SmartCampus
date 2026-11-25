package ru.smartcampus

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Room(val number: String, val building: String, val floor: Int, val routable: Boolean = false) {
    val label get() = "$building · аудитория $number"
}

@Serializable
data class Slot(val date: String, val from: String, val until: String, val room: Room) {
    val time get() = "$from–$until"
}

@Serializable
data class Lesson(val id: String, val subject: String, val current: Slot, val previous: Slot? = null)

@Serializable
data class Schedule(val savedAt: Long, val lessons: List<Lesson>) {
    fun on(date: LocalDate): List<Lesson> = lessons
        .filter { it.current.date == date.toString() }
        .sortedBy { it.current.from }

    fun week(): Map<LocalDate, List<Lesson>> = lessons
        .map { LocalDate.parse(it.current.date) }
        .distinct()
        .sorted()
        .associateWith(::on)
}

internal object DemoCampus {
    val day: LocalDate = LocalDate.of(2026, 11, 11)
    val room312 = Room("312", "Корпус Б", 3, routable = true)
    val rooms = listOf(room312, Room("312", "Корпус А", 3), Room("204", "Корпус А", 2))

    fun schedule(clock: Clock): Schedule {
        fun slot(days: Long, from: String, until: String, room: Room) =
            Slot(day.plusDays(days).toString(), from, until, room)

        return Schedule(clock.millis(), listOf(
            Lesson("databases", "Базы данных", slot(0, "12:20", "13:50", room312),
                slot(0, "10:30", "12:00", Room("205", "Корпус А", 2))),
            Lesson("networks", "Компьютерные сети", slot(0, "14:00", "15:30", rooms.last())),
            Lesson("practice", "Проектный практикум",
                slot(1, "10:30", "12:00", Room("217", "Корпус А", 2))),
        ))
    }

    fun search(number: String): List<Room> = rooms.filter { it.number == number.trim() }
}

@Singleton
class CampusRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("smartcampus_demo", Context.MODE_PRIVATE)

    fun load(offline: Boolean): Schedule? {
        if (offline) {
            val stored = preferences.getString("schedule", null) ?: return null
            return try {
                Json.decodeFromString<Schedule>(stored)
            } catch (_: SerializationException) {
                null
            }
        }
        val fresh = DemoCampus.schedule(Clock.systemUTC())
        preferences.edit().putString("schedule", Json.encodeToString(fresh)).apply()
        return fresh
    }
}
