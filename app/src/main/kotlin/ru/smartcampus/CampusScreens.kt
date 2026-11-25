package ru.smartcampus

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val russian = Locale.forLanguageTag("ru")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", russian)
private val compactDay = DateTimeFormatter.ofPattern("d MMMM", russian)
private val timeStamp = DateTimeFormatter.ofPattern("d MMMM в HH:mm", russian)
    .withZone(ZoneId.systemDefault())

private fun formattedDay(date: LocalDate) = date.format(dayFormat).replaceFirstChar { it.titlecase(russian) }
private fun savedAt(time: Long) = timeStamp.format(Instant.ofEpochMilli(time))

@Composable
internal fun CampusHome(
    state: CampusState,
    onIntent: (CampusIntent) -> Unit,
    onLesson: (Lesson) -> Unit,
    onRoom: (Room) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Tab.entries.forEach { tab ->
                    val title = when (tab) {
                        Tab.Schedule -> "Расписание"
                        Tab.Map -> "Карта"
                        Tab.Alerts -> "Уведомления"
                    }
                    val icon = when (tab) {
                        Tab.Schedule -> R.drawable.ic_campus_calendar
                        Tab.Map -> R.drawable.ic_campus_map
                        Tab.Alerts -> R.drawable.ic_campus_bell
                    }
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { onIntent(CampusIntent.SelectTab(tab)) },
                        icon = { CampusIcon(icon) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { inset ->
        when (state.tab) {
            Tab.Schedule -> Screen(inset) { ScheduleContent(state, onIntent, onLesson) }
            Tab.Map -> Screen(inset) { SearchContent(state, onIntent, onRoom) }
            Tab.Alerts -> Screen(inset) { AlertsContent(state, onLesson) }
        }
    }
}

@Composable
private fun ScheduleContent(state: CampusState, onIntent: (CampusIntent) -> Unit, onLesson: (Lesson) -> Unit) {
    Header("Расписание")
    val monday = DemoCampus.day.minusDays((DemoCampus.day.dayOfWeek.value - 1).toLong())
    val weekTitle = "${monday.dayOfMonth}–${monday.plusDays(6).format(compactDay)}"
    Title(if (state.period == Period.Day) formattedDay(DemoCampus.day) else weekTitle)
    Supporting("Учебная группа ИВТб-4303")
    Segment("День", "Неделя", firstSelected = state.period == Period.Day) {
        onIntent(CampusIntent.SelectPeriod(if (it) Period.Day else Period.Week))
    }
    if (state.offline) {
        val timestamp = state.schedule?.let { "Сохранено ${savedAt(it.savedAt)}. Время и аудитория могли измениться." }
            ?: "Сохранённых данных пока нет. Восстановите связь и попробуйте снова."
        Notice("Нет связи", timestamp, warning = true)
    }

    val schedule = state.schedule
    if (schedule != null) {
        if (state.period == Period.Day) {
            Header(if (state.offline) "Из сохранённой недели" else "Ближайшие занятия")
            val lessons = schedule.on(DemoCampus.day)
            if (lessons.isEmpty()) Supporting("Занятий нет")
            lessons.forEach { LessonRow(it, onLesson) }
        } else {
            schedule.week().forEach { (date, lessons) ->
                Header(formattedDay(date))
                lessons.forEach { LessonRow(it, onLesson) }
            }
        }
        if (!state.offline) Supporting("Обновлено ${savedAt(schedule.savedAt)}")
    }
    OutlinedButton(
        onClick = { onIntent(CampusIntent.Refresh) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(if (state.offline) "Проверить снова" else "Обновить расписание") }
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Демо-режим")
            Supporting("Имитировать отсутствие связи")
        }
        Switch(
            checked = state.offline,
            onCheckedChange = { onIntent(CampusIntent.SetOffline(it)) },
            modifier = Modifier.semantics { contentDescription = "Имитировать отсутствие связи" },
        )
    }
}

@Composable
private fun LessonRow(lesson: Lesson, onLesson: (Lesson) -> Unit) {
    val moved = lesson.previous != null
    val details = if (moved) "Перенесено · ${lesson.current.time}\n${lesson.current.room.label}"
        else "${lesson.current.time} · ${lesson.current.room.label}"
    ListCard(lesson.subject, details, highlighted = moved, onClick = { onLesson(lesson) })
}

@Composable
private fun SearchContent(state: CampusState, onIntent: (CampusIntent) -> Unit, onRoom: (Room) -> Unit) {
    Header("Карта кампуса")
    Title("Найти аудиторию")
    Supporting("Пошаговый план доступен для корпуса Б.")
    val focus = LocalFocusManager.current
    val search = {
        onIntent(CampusIntent.Search)
        focus.clearFocus()
    }
    OutlinedTextField(
        value = state.query,
        onValueChange = { input ->
            if (input.length <= 6 && input.all(Char::isDigit)) onIntent(CampusIntent.EditQuery(input))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Номер аудитории") },
        leadingIcon = { CampusIcon(R.drawable.ic_campus_search) },
        singleLine = true,
        isError = state.emptyQuery,
        supportingText = if (state.emptyQuery) ({ Text("Введите номер аудитории.") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
    )
    PrimaryAction("Найти", search)
    if (!state.submitted) {
        state.schedule?.lessons?.firstOrNull { it.current.room.routable }?.let {
            Header("Из расписания")
            ListCard(it.current.room.label, it.subject, highlighted = true) { onRoom(it.current.room) }
        }
    } else if (!state.emptyQuery && state.matches.isEmpty()) {
        Notice("Аудитория не найдена", "Проверьте номер. Для демонстрации попробуйте 312.", warning = true)
    } else if (state.matches.isNotEmpty()) {
        Header(if (state.matches.size > 1) "Выберите корпус" else "Результат поиска")
        state.matches.forEach { room ->
            ListCard(room.label, if (room.routable) "${room.floor}-й этаж · маршрут доступен"
            else "${room.floor}-й этаж · учебный маршрут отсутствует", highlighted = room.routable) { onRoom(room) }
        }
    }
}

@Composable
private fun AlertsContent(state: CampusState, onLesson: (Lesson) -> Unit) {
    Header("Уведомления")
    Title("Изменения расписания")
    Supporting(if (state.offline) "Из сохранённого расписания" else "Учебные уведомления")
    val changes = state.schedule?.lessons.orEmpty().filter { it.previous != null }
    if (changes.isEmpty()) Supporting("Переносов пока нет")
    changes.forEach {
        ListCard("${it.subject} · перенос", "${it.current.time}\n${it.current.room.label}", true) { onLesson(it) }
    }
}

@Composable
internal fun LessonDetailsScreen(
    lesson: Lesson?, time: Long?, offline: Boolean, onBack: () -> Unit, onRoom: (Room) -> Unit,
) {
    DetailScaffold("Детали пары", onBack, footer = lesson?.let { selected ->
        { PrimaryAction("Маршрут от входа") { onRoom(selected.current.room) } }
    }) {
        if (lesson == null) {
            Notice("Пара недоступна", "Вернитесь к расписанию и обновите данные.", warning = true)
        } else {
            Supporting(formattedDay(LocalDate.parse(lesson.current.date)))
            if (lesson.previous != null) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
                    Text("Пару перенесли", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Title(lesson.subject)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (lesson.previous != null) "Теперь" else "По расписанию")
                    Text(lesson.current.time, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(lesson.current.room.label, style = MaterialTheme.typography.titleLarge)
                    Text("${lesson.current.room.floor}-й этаж")
                }
            }
            lesson.previous?.let { previous ->
                Notice("Было", "${previous.time} · ${previous.room.label}")
            }
            HorizontalDivider()
            Supporting("Источник · учебное расписание")
            time?.let { Supporting(if (offline) "Без связи · сохранено ${savedAt(it)}" else "Обновлено ${savedAt(it)}") }
        }
    }
}

@Composable
internal fun RouteScreen(
    room: Room, offline: Boolean, onBack: () -> Unit, onRetry: () -> Unit,
    onDemoConnection: () -> Unit, onArrival: () -> Unit,
) {
    var upper by rememberSaveable(room) { mutableStateOf(false) }
    val available = room.routable && !offline
    DetailScaffold("Маршрут", onBack, footer = {
        when {
            available -> PrimaryAction(if (upper) "Я на месте" else "Показать 3-й этаж") {
                if (upper) onArrival() else upper = true
            }
            room.routable -> {
                PrimaryAction("Повторить", onRetry)
                TextButton(onClick = onDemoConnection, modifier = Modifier.fillMaxWidth()) {
                    Text("Отключить имитацию сбоя")
                }
            }
            else -> PrimaryAction("Назад", onBack)
        }
    }) {
        Supporting(room.label)
        if (available) {
            Title(if (upper) "К аудитории ${room.number}" else "От главного входа")
            Supporting(if (upper) "3-й этаж · дверь слева" else "${room.building} · около 3 минут")
            Segment("1-й этаж", "3-й этаж", !upper) { upper = !it }
            FloorPlan(upper)
            Notice(
                if (upper) "От лестницы до аудитории 312" else "Лестница справа от вахты",
                if (upper) "Идите по коридору и поверните налево у двери 312." else "Пройдите мимо вахты и поднимитесь на 3-й этаж.",
                accent = true,
            )
        } else if (!room.routable) {
            Title("Маршрут недоступен")
            Notice("План корпуса", "Учебный маршрут подготовлен до аудитории 312 в корпусе Б.")
        } else {
            CampusIcon(R.drawable.ic_campus_route_error, modifier = Modifier.size(26.dp))
            Title("Не загрузился план")
            Notice("Куда идти", "${room.building}, ${room.floor}-й этаж, аудитория ${room.number}. Восстановите связь и повторите.")
        }
    }
}

@Composable
private fun FloorPlan(upper: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val rooms = if (upper) listOf("308", "310", "312", "Лестница", "309", "311", "313", "Служебная")
        else listOf("Гардероб", "Вахта", "Ресепшен", "Лестница", "104", "106", "Вход", "Служебная")
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(200.dp).padding(12.dp)
                .semantics { contentDescription = if (upper) "Схема третьего этажа" else "Схема первого этажа" },
        ) {
            val width = maxWidth / 4
            rooms.forEachIndexed { index, name ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (name == "312" || name == "Вход") MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.offset(x = width * (index % 4) + 2.dp, y = if (index < 4) 6.dp else 114.dp)
                        .width(width - 4.dp).height(54.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(2.dp))
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
                val points = if (upper) listOf(point(.875f, .32f), point(.875f, .49f),
                    point(.625f, .49f), point(.625f, .32f))
                else listOf(point(.625f, .70f), point(.625f, .49f),
                    point(.875f, .49f), point(.875f, .32f))
                points.zipWithNext().forEach { (from, to) ->
                    drawLine(accent, from, to, 3.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(accent, 5.dp.toPx(), points.last())
            }
        }
    }
}

@Composable
internal fun ArrivalScreen(room: Room, onBack: () -> Unit, onSchedule: () -> Unit) {
    DetailScaffold("Маршрут", onBack, footer = { PrimaryAction("К расписанию", onSchedule) }) {
        Title("Вы на месте")
        Notice(room.label, "${room.floor}-й этаж", accent = true)
    }
}

@Composable
private fun DetailScaffold(
    title: String, onBack: () -> Unit, footer: (@Composable ColumnScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
        Column(Modifier.padding(inset).fillMaxSize().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { contentDescription = "Назад" })
                }
                Header(title)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
            footer?.let {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), content = it)
            }
        }
    }
}

@Composable
private fun Screen(insets: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun Header(value: String) = Text(value, Modifier.semantics { heading() },
    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun Title(value: String) = Text(value, Modifier.semantics { heading() },
    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Supporting(value: String) = Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium)

@Composable
private fun CampusIcon(@DrawableRes id: Int, modifier: Modifier = Modifier.size(20.dp)) =
    Icon(painterResource(id), contentDescription = null, modifier = modifier)

@Composable
private fun PrimaryAction(title: String, onClick: () -> Unit) {
    Button(onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(title) }
}

@Composable
private fun Segment(first: String, second: String, firstSelected: Boolean, onSelection: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(first, second).forEachIndexed { index, title ->
            val selected = (index == 0) == firstSelected
            val pick = { onSelection(index == 0) }
            val sizing = Modifier.weight(1f).heightIn(min = 48.dp)
            if (selected) FilledTonalButton(pick, modifier = sizing) { Text(title) }
            else TextButton(pick, modifier = sizing) { Text(title) }
        }
    }
}

@Composable
private fun Notice(title: String, details: String, warning: Boolean = false, accent: Boolean = false) {
    val palette = MaterialTheme.colorScheme
    val (background, content) = when {
        warning -> palette.tertiaryContainer to palette.onTertiaryContainer
        accent -> palette.primaryContainer to palette.onPrimaryContainer
        else -> palette.surfaceVariant to palette.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(), color = background, contentColor = content, shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(details)
        }
    }
}

@Composable
private fun ListCard(title: String, details: String, highlighted: Boolean, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) palette.primaryContainer else palette.surfaceVariant,
            contentColor = if (highlighted) palette.onPrimaryContainer else palette.onSurfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
            CampusIcon(R.drawable.ic_campus_chevron, Modifier.size(20.dp))
        }
    }
}
