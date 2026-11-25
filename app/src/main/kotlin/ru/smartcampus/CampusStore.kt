package ru.smartcampus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

internal enum class Tab { Schedule, Map, Alerts }
internal enum class Period { Day, Week }

internal data class CampusState(
    val tab: Tab = Tab.Schedule,
    val period: Period = Period.Day,
    val offline: Boolean = false,
    val schedule: Schedule? = null,
    val query: String = "",
    val submitted: Boolean = false,
    val recentRooms: List<String> = emptyList(),
) {
    val matches get() = if (submitted) DemoCampus.search(query) else emptyList()
    val emptyQuery get() = submitted && query.isBlank()
}

internal sealed interface CampusIntent {
    data class SelectTab(val tab: Tab) : CampusIntent
    data class SelectPeriod(val period: Period) : CampusIntent
    data class EditQuery(val value: String) : CampusIntent
    data class SetOffline(val value: Boolean) : CampusIntent
    data object Search : CampusIntent
    data object Refresh : CampusIntent
}

internal fun reduce(state: CampusState, intent: CampusIntent): CampusState = when (intent) {
    is CampusIntent.SelectTab -> state.copy(tab = intent.tab)
    is CampusIntent.SelectPeriod -> state.copy(period = intent.period)
    is CampusIntent.EditQuery -> state.copy(query = intent.value, submitted = false)
    is CampusIntent.SetOffline -> state.copy(offline = intent.value)
    CampusIntent.Search -> {
        val number = state.query.trim()
        val recent = if (DemoCampus.search(number).isEmpty()) state.recentRooms
            else (listOf(number) + state.recentRooms).distinct().take(4)
        state.copy(submitted = true, recentRooms = recent)
    }
    CampusIntent.Refresh -> state
}

@HiltViewModel
internal class CampusViewModel @Inject constructor(
    private val repository: CampusRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val offline = savedState["offline"] ?: false
    private val mutableState = MutableStateFlow(CampusState(
        tab = Tab.valueOf(savedState["tab"] ?: Tab.Schedule.name),
        period = Period.valueOf(savedState["period"] ?: Period.Day.name),
        offline = offline,
        schedule = repository.load(offline),
        query = savedState["query"] ?: "",
        submitted = savedState["submitted"] ?: false,
        recentRooms = savedState.get<ArrayList<String>>("recentRooms").orEmpty(),
    ))
    val state = mutableState.asStateFlow()

    fun accept(intent: CampusIntent) {
        val next = reduce(mutableState.value, intent)
        mutableState.value = when (intent) {
            is CampusIntent.SetOffline, CampusIntent.Refresh ->
                next.copy(schedule = repository.load(next.offline))
            else -> next
        }
        val current = mutableState.value
        savedState["tab"] = current.tab.name
        savedState["period"] = current.period.name
        savedState["offline"] = current.offline
        savedState["query"] = current.query
        savedState["submitted"] = current.submitted
        savedState["recentRooms"] = ArrayList(current.recentRooms)
    }
}
