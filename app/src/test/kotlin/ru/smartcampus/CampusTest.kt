package ru.smartcampus

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset

class CampusTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T06:18:00Z"), ZoneOffset.UTC)

    @Test
    fun dayAndWeekShowLessonsInChronologicalOrder() {
        val schedule = DemoCampus.schedule(clock)

        assertEquals(listOf("08:30", "10:10", "12:20", "14:00", "15:40"), schedule.on(DemoCampus.day).map { it.current.from })
        assertEquals((9..13).map(DemoCampus.day::withDayOfMonth), schedule.week().keys.toList())
        assertTrue(schedule.on(DemoCampus.day.plusDays(3)).isEmpty())
    }

    @Test
    fun cachedSnapshotPreservesTimestampAndBothLocations() {
        val loaded = DemoCampus.schedule(clock)
        val restored = Json.decodeFromString<Schedule>(Json.encodeToString(loaded))
        val moved = restored.on(DemoCampus.day).single { it.previous != null }

        assertEquals(clock.millis(), restored.savedAt)
        assertEquals("205", moved.previous?.room?.number)
        assertEquals("312", moved.current.room.number)
    }

    @Test
    fun roomSearchOrdersTheRoutableMatchFirstAndKeepsTheOtherBuilding() {
        val withQuery = reduce(CampusState(), CampusIntent.EditQuery("312"))
        assertEquals(RoomSearch.Idle, withQuery.search)
        assertEquals(RoomSearch.Loading, reduce(withQuery, CampusIntent.Search).search)

        val matches = DemoCampus.search("312")
        assertEquals(listOf("Корпус Б", "Корпус А"), matches.map { it.building })
        assertTrue(matches.first().routable)
        assertFalse(matches.last().routable)
    }

    @Test
    fun newInputClearsPreviousSearchAndEmptySubmitHasItsOwnState() {
        val submitted = reduce(CampusState(), CampusIntent.Search)
        assertEquals(RoomSearch.EmptyQuery, submitted.search)

        val edited = reduce(submitted, CampusIntent.EditQuery("999"))
        assertEquals(RoomSearch.Idle, edited.search)
        assertEquals(RoomSearch.Loading, reduce(edited, CampusIntent.Search).search)
        assertTrue(DemoCampus.search("999").isEmpty())
    }

    @Test
    fun recentRoomSearchesKeepFourSuccessfulUniqueQueriesInOrder() {
        val state = listOf("114", "312", "204", "205", "217", "204", "312", "999", "")
            .fold(CampusState()) { current, query ->
                completeRoomSearch(current, query, DemoCampus.search(query))
            }

        assertEquals(listOf("312", "204", "217", "205"), state.recentRooms)
    }

    @Test
    fun selectingPeriodPreservesTheCachedScheduleAndConnectionState() {
        val initial = CampusState(offline = true, schedule = DemoCampus.schedule(clock))
        val updated = reduce(initial, CampusIntent.SelectPeriod(Period.Week))

        assertEquals(Period.Week, updated.period)
        assertTrue(updated.offline)
        assertEquals(initial.schedule, updated.schedule)
    }

    @Test
    fun mockScheduleAndChangeHistoryStayWithinNovember() {
        val lessons = DemoCampus.schedule(clock).lessons
        assertEquals(12, lessons.size)
        assertEquals(4, lessons.count { it.previous != null })
        assertEquals(12, lessons.map { it.id }.distinct().size)
        assertTrue(lessons.flatMap { listOfNotNull(it.current, it.previous) }
            .all { LocalDate.parse(it.date).month == Month.NOVEMBER })
        assertTrue(lessons.any { it.subject.length > 70 })
    }

    @Test
    fun demoCredentialsAndFormEditsAreDeterministic() {
        assertTrue(acceptsDemoCredentials("stud", "stud"))
        assertFalse(acceptsDemoCredentials("stud", "wrong"))
        assertFalse(acceptsDemoCredentials("other", "stud"))

        val failed = CampusState(session = Session.SignedOut, authError = "error")
        val edited = reduce(failed, CampusIntent.EditLogin("stud"))
        assertEquals("stud", edited.login)
        assertEquals(null, edited.authError)
    }

    @Test
    fun routeFloorAndReturnToScheduleUseReducer() {
        val initial = CampusState(tab = Tab.Map, route = RouteState(DemoCampus.room312, false, RoutePhase.Ready))
        val upper = reduce(initial, CampusIntent.SelectFloor(true))
        assertTrue(upper.route?.upperFloor == true)
        assertEquals(RoutePhase.Ready, upper.route?.phase)
        assertEquals(Tab.Schedule, reduce(upper, CampusIntent.ReturnToSchedule).tab)
    }
}
