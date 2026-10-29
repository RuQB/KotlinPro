package com.example.calculator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.CalculatorEngine
import com.example.calculator.R

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9F0A),
    onPrimary = Color(0xFF1C1C1E),
    secondary = Color(0xFF2FBFAE),
    onSecondary = Color(0xFF06231E),
    background = Color(0xFF141419),
    onBackground = Color(0xFFF3F3F7),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFF3F3F7),
    surfaceVariant = Color(0xFF232329),
    onSurfaceVariant = Color(0xFFF3F3F7),
    secondaryContainer = Color(0xFF2C2C34),
    onSecondaryContainer = Color(0xFFE8E8EE),
    errorContainer = Color(0xFF3C2226),
    onErrorContainer = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE07C00),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0F9C86),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF1A1A20),
    surface = Color(0xFFF6F6FA),
    onSurface = Color(0xFF1A1A20),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF1A1A20),
    secondaryContainer = Color(0xFFE4E4EC),
    onSecondaryContainer = Color(0xFF1A1A20),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun CalculatorApp() {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CalculatorScreen()
        }
    }
}

@Composable
private fun CalculatorScreen() {
    val state = remember { CalcState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Display(state = state, modifier = Modifier.weight(1f))
        Keypad(
            state = state,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun Display(state: CalcState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val expr = if (state.justEvaluated) state.lastExpression.orEmpty() else state.expression
    val result = when {
        state.error -> state.errorMessage ?: stringResource(R.string.calc_error)
        else -> state.input.ifEmpty { "0" }
    }
    val hint = if (state.error) null else CalculatorEngine.decimalHint(state.input)

    Box(modifier.fillMaxWidth()) {
        if (state.hasMemory) {
            Text(
                text = "M",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = cs.secondary,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            if (expr.isNotEmpty()) {
                Text(
                    text = expr,
                    fontSize = when {
                        expr.length <= 14 -> 20.sp
                        expr.length <= 22 -> 17.sp
                        else -> 14.sp
                    },
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = result,
                fontSize = when {
                    result.length <= 8 -> 52.sp
                    result.length <= 11 -> 42.sp
                    result.length <= 15 -> 32.sp
                    else -> 25.sp
                },
                fontWeight = FontWeight.Medium,
                color = if (state.error) cs.onErrorContainer else cs.onBackground,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            hint?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.notice?.let { n ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = n,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.secondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private enum class KeyType { DIGIT, FUNC, OP, DANGER, MU, EQUALS }

@Composable
private fun Keypad(state: CalcState, modifier: Modifier = Modifier) {
    val rows: List<List<Pair<String, KeyType>>> = listOf(
        listOf(
            "C" to KeyType.DANGER,
            "⌫" to KeyType.FUNC,
            "%" to KeyType.FUNC,
            "÷" to KeyType.OP
        ),
        listOf(
            "7" to KeyType.DIGIT, "8" to KeyType.DIGIT, "9" to KeyType.DIGIT,
            "×" to KeyType.OP
        ),
        listOf(
            "4" to KeyType.DIGIT, "5" to KeyType.DIGIT, "6" to KeyType.DIGIT,
            "−" to KeyType.OP
        ),
        listOf(
            "1" to KeyType.DIGIT, "2" to KeyType.DIGIT, "3" to KeyType.DIGIT,
            "+" to KeyType.OP
        ),
        listOf(
            "MR" to KeyType.FUNC, "MS" to KeyType.FUNC, "M+" to KeyType.FUNC,
            "MU" to KeyType.MU
        ),
        listOf(
            "/" to KeyType.DIGIT, "0" to KeyType.DIGIT, "." to KeyType.DIGIT,
            "=" to KeyType.EQUALS
        ),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, type) ->
                    CalcKey(
                        label = label,
                        type = type,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        onClick = { state.onKey(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    type: KeyType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = when (type) {
        KeyType.DIGIT -> cs.surfaceVariant to cs.onSurfaceVariant
        KeyType.FUNC -> cs.secondaryContainer to cs.onSecondaryContainer
        KeyType.DANGER -> cs.errorContainer to cs.onErrorContainer
        KeyType.OP, KeyType.EQUALS -> cs.primary to cs.onPrimary
        KeyType.MU -> cs.secondary to cs.onSecondary
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = if (label.length > 1) 21.sp else 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
