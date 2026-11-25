package ru.smartcampus

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CampusTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-11T06:18:00Z"), ZoneOffset.UTC)

    @Test
    fun dayAndWeekShowLessonsInChronologicalOrder() {
        val schedule = DemoCampus.schedule(clock)

        assertEquals(listOf("12:20", "14:00"), schedule.on(DemoCampus.day).map { it.current.from })
        assertEquals(listOf(DemoCampus.day, DemoCampus.day.plusDays(1)), schedule.week().keys.toList())
        assertTrue(schedule.on(DemoCampus.day.plusDays(2)).isEmpty())
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
        assertTrue(withQuery.matches.isEmpty())

        val searched = reduce(withQuery, CampusIntent.Search)
        assertEquals(listOf("Корпус Б", "Корпус А"), searched.matches.map { it.building })
        assertTrue(searched.matches.first().routable)
        assertFalse(searched.matches.last().routable)
    }

    @Test
    fun newInputClearsPreviousSearchAndEmptySubmitHasItsOwnState() {
        val submitted = reduce(CampusState(), CampusIntent.Search)
        assertTrue(submitted.emptyQuery)

        val edited = reduce(submitted, CampusIntent.EditQuery("999"))
        assertFalse(edited.submitted)
        val searched = reduce(edited, CampusIntent.Search)
        assertFalse(searched.emptyQuery)
        assertTrue(searched.matches.isEmpty())
    }

    @Test
    fun selectingPeriodPreservesTheCachedScheduleAndConnectionState() {
        val initial = CampusState(offline = true, schedule = DemoCampus.schedule(clock))
        val updated = reduce(initial, CampusIntent.SelectPeriod(Period.Week))

        assertEquals(Period.Week, updated.period)
        assertTrue(updated.offline)
        assertEquals(initial.schedule, updated.schedule)
    }
}
