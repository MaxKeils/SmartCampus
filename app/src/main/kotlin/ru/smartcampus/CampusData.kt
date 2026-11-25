package ru.smartcampus

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
data class Lesson(
    val id: String,
    val subject: String,
    val current: Slot,
    val previous: Slot? = null,
    val note: String? = null,
)

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
    private val rooms = listOf(
        room312, Room("312", "Корпус А", 3), Room("204", "Корпус А", 2),
        Room("205", "Корпус А", 2), Room("217", "Корпус А", 2), Room("304", "Корпус А", 3),
        Room("114", "Корпус Б", 1), Room("310", "Корпус Б", 3), Room("314", "Корпус Б", 3),
        Room("206", "Корпус В", 2), Room("221", "Корпус В", 2), Room("410", "Корпус В", 4),
    )

    fun schedule(clock: Clock): Schedule {
        fun slot(dayOfMonth: Int, from: String, until: String, room: Int) =
            Slot(day.withDayOfMonth(dayOfMonth).toString(), from, until, rooms[room])

        return Schedule(clock.millis(), listOf(
            Lesson("logic", "Логика", slot(11, "08:30", "10:00", 6)),
            Lesson("methods", "Методы анализа и проектирования информационных систем", slot(11, "10:10", "11:40", 4)),
            Lesson("databases", "Базы данных", slot(11, "12:20", "13:50", 0),
                slot(11, "10:30", "12:00", 3), "Лекция перенесена в корпус Б. Тема и состав группы прежние."),
            Lesson("networks", "Компьютерные сети", slot(11, "14:00", "15:30", 2)),
            Lesson("ethics", "Цифровая этика", slot(11, "15:40", "17:10", 9)),
            Lesson("math", "Дискретная математика", slot(9, "10:10", "11:40", 5)),
            Lesson("design", "Проектирование пользовательских интерфейсов", slot(9, "12:20", "13:50", 7),
                slot(9, "10:10", "11:40", 8), "Практика начнётся позже. Перед занятием откройте ноябрьский макет и подготовьте два примера экранов для разбора."),
            Lesson("english", "Английский", slot(10, "12:20", "13:50", 10)),
            Lesson("practice", "Проектный практикум", slot(12, "10:30", "12:00", 4)),
            Lesson("quality", "Управление качеством и рисками в проектах разработки программного обеспечения", slot(12, "14:00", "15:30", 11),
                slot(12, "14:00", "15:30", 5), "Изменилась только аудитория."),
            Lesson("systems", "Архитектура распределённых приложений и интеграция университетских сервисов", slot(13, "10:10", "11:40", 8),
                slot(12, "15:40", "17:10", 9), "Занятие перенесено на пятницу. Для практики подготовьте схему обмена данными и краткое описание обработки ошибок источника."),
            Lesson("seminar", "Семинар", slot(13, "12:20", "13:50", 1)),
        ))
    }

    fun search(number: String): List<Room> = rooms.filter { it.number == number.trim() }
}

private fun <T> mockResponse(response: suspend () -> T) = flow {
    delay((800L..1500L).random())
    emit(response())
}.flowOn(Dispatchers.IO)

@Singleton
class CampusRepository @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("smartcampus_demo", Context.MODE_PRIVATE)

    fun load(offline: Boolean) = if (offline) {
        flow { emit(cachedSchedule()) }.flowOn(Dispatchers.IO)
    } else {
        mockResponse<Schedule?> {
            val fresh = DemoCampus.schedule(Clock.systemUTC())
            preferences.edit().putString("schedule", Json.encodeToString(fresh)).apply()
            fresh
        }
    }

    fun search(number: String) = mockResponse { DemoCampus.search(number) }

    fun route(room: Room, offline: Boolean) = mockResponse { room.routable && !offline }

    private fun cachedSchedule(): Schedule? {
        val stored = preferences.getString("schedule", null) ?: return null
        return try {
            Json.decodeFromString<Schedule>(stored)
        } catch (_: SerializationException) {
            null
        }
    }
}

private val Context.sessionStore by preferencesDataStore(name = "campus_session")
private val signedInKey = booleanPreferencesKey("signed_in")

@Singleton
class DemoAuthRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.sessionStore
    val session = store.data.map { it[signedInKey] == true }

    fun signIn(login: String, password: String) = mockResponse {
        val accepted = acceptsDemoCredentials(login, password)
        if (accepted) store.edit { it[signedInKey] = true }
        accepted
    }

    suspend fun signOut() {
        store.edit { it.remove(signedInKey) }
    }
}

internal fun acceptsDemoCredentials(login: String, password: String) =
    login.trim() == "stud" && password == "stud"
