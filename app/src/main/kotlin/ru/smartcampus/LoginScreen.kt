package ru.smartcampus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun LoginScreen(state: CampusState, onIntent: (CampusIntent) -> Unit) {
    val focus = LocalFocusManager.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "SmartCampus", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                when (state.session) {
                    Session.Restoring -> LoadingSkeleton("Восстановление входа", rows = 2)
                    Session.Unavailable -> {
                        Text("Не удалось восстановить сеанс", style = MaterialTheme.typography.headlineMedium)
                        Notice("Локальное хранилище недоступно", "Повторите попытку.", warning = true)
                        PrimaryAction("Повторить") { onIntent(CampusIntent.RestoreSession) }
                    }
                    Session.SignedOut -> {
                        val passwordField = remember { TextFieldState(initialText = state.password) }
                        val currentPassword by rememberUpdatedState(state.password)
                        LaunchedEffect(passwordField) {
                            snapshotFlow { passwordField.text.toString() }.collect {
                                if (it != currentPassword) onIntent(CampusIntent.EditPassword(it))
                            }
                        }
                        val submit = {
                            focus.clearFocus()
                            onIntent(CampusIntent.EditPassword(passwordField.text.toString()))
                            onIntent(CampusIntent.SignIn)
                        }
                        Text(
                            "Вход в приложение", Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        )
                        OutlinedTextField(
                            value = state.login,
                            onValueChange = { onIntent(CampusIntent.EditLogin(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Логин") },
                            enabled = !state.authBusy,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedSecureTextField(
                            state = passwordField,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Пароль") },
                            enabled = !state.authBusy,
                            textObfuscationMode = TextObfuscationMode.Hidden,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            onKeyboardAction = { submit() },
                        )
                        state.authError?.let { Notice("Вход не выполнен", it, warning = true) }
                        if (state.authBusy) LoadingSkeleton("Выполняется вход", rows = 1)
                        else PrimaryAction("Войти", onClick = submit)
                    }
                    Session.SignedIn -> Unit
                }
            }
        }
    }
}

@Composable
internal fun LoadingSkeleton(label: String, rows: Int = 3) {
    val pulse by rememberInfiniteTransition(label = "campus-skeleton").animateFloat(
        initialValue = -380f,
        targetValue = 760f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing)),
        label = "skeleton-highlight",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.primaryContainer, 0.55f)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(pulse, 0f),
        end = Offset(pulse + 360f, 220f),
    )
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { index ->
            Box(Modifier.fillMaxWidth().background(brush, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(if (index == 1) 0.42f else 0.7f)
                        .height(15.dp).background(highlight, RoundedCornerShape(5.dp)))
                    Box(Modifier.fillMaxWidth(if (index == 2) 0.65f else 0.9f)
                        .height(11.dp).background(highlight, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
