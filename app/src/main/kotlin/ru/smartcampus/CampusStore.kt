package ru.smartcampus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

internal enum class Tab { Schedule, Map, Alerts }
internal enum class Period { Day, Week }
internal enum class Session { Restoring, SignedOut, SignedIn, Unavailable }
internal enum class RoutePhase { Loading, Ready, Unavailable }

internal sealed interface RoomSearch {
    data object Idle : RoomSearch
    data object EmptyQuery : RoomSearch
    data object Loading : RoomSearch
    data class Ready(val rooms: List<Room>) : RoomSearch
}

internal data class RouteState(
    val room: Room,
    val offline: Boolean,
    val phase: RoutePhase = RoutePhase.Loading,
    val upperFloor: Boolean = false,
)

internal data class CampusState(
    val session: Session = Session.Restoring,
    val login: String = "",
    val password: String = "",
    val authBusy: Boolean = false,
    val authError: String? = null,
    val tab: Tab = Tab.Schedule,
    val period: Period = Period.Day,
    val offline: Boolean = false,
    val schedule: Schedule? = null,
    val loadingSchedule: Boolean = false,
    val query: String = "",
    val search: RoomSearch = RoomSearch.Idle,
    val recentRooms: List<String> = emptyList(),
    val route: RouteState? = null,
)

internal sealed interface CampusIntent {
    data object RestoreSession : CampusIntent
    data class EditLogin(val value: String) : CampusIntent
    data class EditPassword(val value: String) : CampusIntent
    data object SignIn : CampusIntent
    data object SignOut : CampusIntent
    data class SelectTab(val tab: Tab) : CampusIntent
    data class SelectPeriod(val period: Period) : CampusIntent
    data class EditQuery(val value: String) : CampusIntent
    data class SetOffline(val value: Boolean) : CampusIntent
    data object Search : CampusIntent
    data object Refresh : CampusIntent
    data class OpenLesson(val id: String) : CampusIntent
    data class OpenRoom(val room: Room) : CampusIntent
    data class EnsureRoute(val room: Room) : CampusIntent
    data object RetryRoute : CampusIntent
    data class SelectFloor(val upper: Boolean) : CampusIntent
    data object AdvanceRoute : CampusIntent
    data object Back : CampusIntent
    data object ReturnToSchedule : CampusIntent
}

internal sealed interface CampusEffect {
    data class ShowLesson(val id: String) : CampusEffect
    data class ShowRoute(val room: Room) : CampusEffect
    data class ShowArrival(val room: Room) : CampusEffect
    data object Back : CampusEffect
    data object ResetStack : CampusEffect
}

internal fun reduce(state: CampusState, intent: CampusIntent): CampusState = when (intent) {
    is CampusIntent.EditLogin -> state.copy(login = intent.value, authError = null)
    is CampusIntent.EditPassword -> state.copy(password = intent.value, authError = null)
    is CampusIntent.SelectTab -> state.copy(tab = intent.tab)
    is CampusIntent.SelectPeriod -> state.copy(period = intent.period)
    is CampusIntent.EditQuery -> state.copy(query = intent.value, search = RoomSearch.Idle)
    is CampusIntent.SetOffline -> state.copy(offline = intent.value)
    CampusIntent.Search -> state.copy(
        search = if (state.query.isBlank()) RoomSearch.EmptyQuery else RoomSearch.Loading,
    )
    is CampusIntent.SelectFloor -> state.copy(
        route = state.route?.copy(upperFloor = intent.upper),
    )
    CampusIntent.ReturnToSchedule -> state.copy(tab = Tab.Schedule)
    else -> state
}

internal fun completeRoomSearch(state: CampusState, query: String, rooms: List<Room>): CampusState {
    val recentRooms = if (rooms.isEmpty()) state.recentRooms
        else (listOf(query.trim()) + state.recentRooms).distinct().take(4)
    return state.copy(search = RoomSearch.Ready(rooms), recentRooms = recentRooms)
}

@HiltViewModel
internal class CampusViewModel @Inject constructor(
    private val repository: CampusRepository,
    private val auth: DemoAuthRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CampusState(
        tab = Tab.valueOf(savedState["tab"] ?: Tab.Schedule.name),
        period = Period.valueOf(savedState["period"] ?: Period.Day.name),
        offline = savedState["offline"] ?: false,
        query = savedState["query"] ?: "",
        recentRooms = savedState.get<ArrayList<String>>("recentRooms").orEmpty(),
    ))
    val state = mutableState.asStateFlow()
    private val effects = Channel<CampusEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    private var scheduleJob: Job? = null
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var sessionJob: Job? = null

    init {
        restoreSession()
    }

    fun accept(intent: CampusIntent) {
        val current = mutableState.value
        when (intent) {
            CampusIntent.RestoreSession -> {
                if (current.session == Session.Unavailable) restoreSession()
                return
            }
            is CampusIntent.EditLogin, is CampusIntent.EditPassword -> {
                if (current.session == Session.SignedOut && !current.authBusy) {
                    mutableState.value = reduce(current, intent)
                }
                return
            }
            CampusIntent.SignIn -> {
                if (current.session == Session.SignedOut && !current.authBusy) signIn(current)
                return
            }
            else -> if (current.session != Session.SignedIn || current.authBusy) return
        }

        if (intent == CampusIntent.Search && current.search == RoomSearch.Loading) return
        if (intent is CampusIntent.EditQuery) searchJob?.cancel()

        val next = reduce(current, intent)
        mutableState.value = next
        saveUi(next)

        when (intent) {
            CampusIntent.SignOut -> signOut()
            CampusIntent.Refresh -> loadSchedule()
            is CampusIntent.SetOffline -> loadSchedule()
            CampusIntent.Search -> if (next.search == RoomSearch.Loading) searchRooms(next.query)
            is CampusIntent.OpenLesson -> effects.trySend(CampusEffect.ShowLesson(intent.id))
            is CampusIntent.OpenRoom -> {
                loadRoute(intent.room)
                effects.trySend(CampusEffect.ShowRoute(intent.room))
            }
            is CampusIntent.EnsureRoute -> {
                if (next.route?.room != intent.room || next.route.offline != next.offline) loadRoute(intent.room)
            }
            CampusIntent.RetryRoute -> next.route?.room?.let(::loadRoute)
            CampusIntent.AdvanceRoute -> {
                next.route?.takeIf { it.phase == RoutePhase.Ready }?.let { route ->
                    if (route.upperFloor) effects.trySend(CampusEffect.ShowArrival(route.room))
                    else mutableState.value = reduce(next, CampusIntent.SelectFloor(true))
                }
            }
            CampusIntent.Back -> effects.trySend(CampusEffect.Back)
            CampusIntent.ReturnToSchedule -> effects.trySend(CampusEffect.ResetStack)
            else -> Unit
        }
    }

    private fun restoreSession() {
        if (sessionJob?.isActive == true) return
        mutableState.value = mutableState.value.copy(session = Session.Restoring)
        sessionJob = viewModelScope.launch {
            try {
                val signedIn = auth.session.first()
                mutableState.value = if (signedIn) mutableState.value.copy(session = Session.SignedIn)
                    else CampusState(session = Session.SignedOut)
                if (signedIn) loadSchedule()
            } catch (_: IOException) {
                mutableState.value = mutableState.value.copy(session = Session.Unavailable)
            }
        }
    }

    private fun signIn(input: CampusState) {
        if (input.login.isBlank() || input.password.isBlank()) {
            mutableState.value = input.copy(authError = "Введите логин и пароль.")
            return
        }
        mutableState.value = input.copy(authBusy = true, authError = null)
        viewModelScope.launch {
            try {
                if (auth.signIn(input.login, input.password).first()) {
                    val signedIn = CampusState(session = Session.SignedIn)
                    saveUi(signedIn)
                    effects.trySend(CampusEffect.ResetStack)
                    mutableState.value = signedIn
                    loadSchedule()
                } else {
                    mutableState.value = input.copy(authError = "Неверный логин или пароль.")
                }
            } catch (_: IOException) {
                mutableState.value = input.copy(authError = "Не удалось сохранить вход. Попробуйте ещё раз.")
            }
        }
    }

    private fun signOut() {
        mutableState.value = mutableState.value.copy(authBusy = true, authError = null)
        viewModelScope.launch {
            try {
                auth.signOut()
                scheduleJob?.cancel()
                searchJob?.cancel()
                routeJob?.cancel()
                val signedOut = CampusState(session = Session.SignedOut)
                saveUi(signedOut)
                effects.trySend(CampusEffect.ResetStack)
                mutableState.value = signedOut
            } catch (_: IOException) {
                mutableState.value = mutableState.value.copy(
                    authBusy = false, authError = "Не удалось выйти. Попробуйте ещё раз.",
                )
            }
        }
    }

    private fun loadSchedule() {
        scheduleJob?.cancel()
        val offline = mutableState.value.offline
        mutableState.value = mutableState.value.copy(loadingSchedule = true)
        scheduleJob = viewModelScope.launch {
            val schedule = repository.load(offline).first()
            mutableState.value = mutableState.value.copy(schedule = schedule, loadingSchedule = false)
        }
    }

    private fun searchRooms(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val result = repository.search(query).first()
            val current = mutableState.value
            if (current.query != query || current.search != RoomSearch.Loading) return@launch
            val updated = completeRoomSearch(current, query, result)
            mutableState.value = updated
            saveUi(updated)
        }
    }

    private fun loadRoute(room: Room) {
        routeJob?.cancel()
        val loading = RouteState(room = room, offline = mutableState.value.offline)
        mutableState.value = mutableState.value.copy(route = loading)
        routeJob = viewModelScope.launch {
            val available = repository.route(room, loading.offline).first()
            val phase = if (available) RoutePhase.Ready else RoutePhase.Unavailable
            mutableState.value = mutableState.value.copy(route = loading.copy(phase = phase))
        }
    }

    private fun saveUi(value: CampusState) {
        savedState["tab"] = value.tab.name
        savedState["period"] = value.period.name
        savedState["offline"] = value.offline
        savedState["query"] = value.query
        savedState["recentRooms"] = ArrayList(value.recentRooms)
    }
}
