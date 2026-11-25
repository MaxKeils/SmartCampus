package ru.smartcampus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
private data object Home : NavKey

@Serializable
private data class LessonDetails(val lessonId: String) : NavKey

@Serializable
private data class RoomRoute(val room: Room) : NavKey

@Serializable
private data class Arrival(val room: Room) : NavKey

@Composable
internal fun CampusNavigation(model: CampusViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    NavDisplay(
        backStack = backStack,
        onBack = ::back,
        entryProvider = entryProvider {
            entry<Home> {
                CampusHome(
                    state = state,
                    onIntent = model::accept,
                    onLesson = { backStack.add(LessonDetails(it.id)) },
                    onRoom = { backStack.add(RoomRoute(it)) },
                )
            }
            entry<LessonDetails> { key ->
                LessonDetailsScreen(
                    state.schedule?.lessons?.find { it.id == key.lessonId },
                    state.schedule?.savedAt,
                    state.offline,
                    onBack = ::back,
                    onRoom = { backStack.add(RoomRoute(it)) },
                )
            }
            entry<RoomRoute> { key ->
                RouteScreen(
                    room = key.room,
                    offline = state.offline,
                    onBack = ::back,
                    onRetry = { model.accept(CampusIntent.Refresh) },
                    onDemoConnection = { model.accept(CampusIntent.SetOffline(false)) },
                    onArrival = { backStack.add(Arrival(key.room)) },
                )
            }
            entry<Arrival> { key ->
                ArrivalScreen(
                    room = key.room,
                    onBack = ::back,
                    onSchedule = {
                        model.accept(CampusIntent.SelectTab(Tab.Schedule))
                        while (backStack.size > 1) back()
                    },
                )
            }
        },
    )
}
