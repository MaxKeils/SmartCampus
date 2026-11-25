package ru.smartcampus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    LaunchedEffect(model, backStack) {
        fun open(key: NavKey) {
            if (backStack.lastOrNull() != key) backStack.add(key)
        }

        model.effect.collect { effect ->
            when (effect) {
                is CampusEffect.ShowLesson -> open(LessonDetails(effect.id))
                is CampusEffect.ShowRoute -> open(RoomRoute(effect.room))
                is CampusEffect.ShowArrival -> open(Arrival(effect.room))
                CampusEffect.Back -> if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else Unit
                CampusEffect.ResetStack -> {
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }
            }
        }
    }

    if (state.session != Session.SignedIn) {
        LoginScreen(state, model::accept)
        return
    }

    NavDisplay(
        backStack = backStack,
        onBack = { model.accept(CampusIntent.Back) },
        entryProvider = entryProvider {
            entry<Home> { CampusHome(state, model::accept) }
            entry<LessonDetails> { key ->
                LessonDetailsScreen(
                    lesson = state.schedule?.lessons?.find { it.id == key.lessonId },
                    time = state.schedule?.savedAt,
                    offline = state.offline,
                    loading = state.loadingSchedule,
                    onIntent = model::accept,
                )
            }
            entry<RoomRoute> { key ->
                LaunchedEffect(key.room, state.offline) {
                    model.accept(CampusIntent.EnsureRoute(key.room))
                }
                RouteScreen(
                    route = state.route?.takeIf { it.room == key.room && it.offline == state.offline }
                        ?: RouteState(key.room, state.offline),
                    onIntent = model::accept,
                )
            }
            entry<Arrival> { key -> ArrivalScreen(key.room, model::accept) }
        },
    )
}
