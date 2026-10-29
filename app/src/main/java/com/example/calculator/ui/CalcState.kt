package com.example.calculator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.calculator.CalculatorEngine
import com.example.calculator.Rational
import com.example.calculator.toRational

/**
 * Состояние калькулятора.
 *
 * tokens — завершённые элементы выражения (числа, "10%", "2/5", операторы),
 * input — число, которое набирается прямо сейчас (может содержать "/").
 */
class CalcState {

    val tokens = mutableStateListOf<String>()
    var input by mutableStateOf("")
    var lastExpression by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var error by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var justEvaluated by mutableStateOf(false)
    var hasMemory by mutableStateOf(false)
    private var memory: Rational = Rational.ZERO

    /** После MS/M+/MR набор цифр начинает новое число, а не дописывает показанное. */
    private var entryFresh by mutableStateOf(false)

    /** Текущее выражение для верхней строки дисплея. */
    val expression: String
        get() {
            val list = if (input.isNotEmpty()) tokens + input else tokens.toList()
            return list.joinToString(" ")
        }

    fun onKey(label: String) {
        when (label) {
            "C" -> clearAll()
            "⌫" -> backspace()
            "%" -> percent()
            "=" -> equalsKey()
            "." -> dot()
            "/" -> fraction()
            "MU" -> mu()
            "MR" -> memoryRecall()
            "MS" -> memoryStore()
            "M+" -> memoryAdd()
            CalculatorEngine.OP_PLUS -> op(CalculatorEngine.OP_PLUS)
            CalculatorEngine.OP_MINUS -> op(CalculatorEngine.OP_MINUS)
            CalculatorEngine.OP_MUL -> op(CalculatorEngine.OP_MUL)
            CalculatorEngine.OP_DIV -> op(CalculatorEngine.OP_DIV)
            else -> digit(label)
        }
    }

    fun digit(d: String) {
        prepareForTyping()
        if (input.length >= 21) return
        input = when {
            input == "0" -> d
            input == "−0" -> "−$d"
            else -> input + d
        }
    }

    fun dot() {
        prepareForTyping()
        // точка разрешена один раз в текущей части числа (числитель или знаменатель)
        val cur = if (input.contains('/')) input.substringAfter('/') else input
        if (!cur.contains('.')) {
            input = when {
                cur.isEmpty() -> "$input" + "0."
                cur == "−" -> "$input" + "0."
                else -> "$input."
            }
        }
    }

    /** Дробь: набираем "2", жмём «/», набираем "5" → операнд "2/5". */
    fun fraction() {
        prepareForTyping()
        if (input.contains("/")) return
        if (input.isEmpty() || input == "−") return // нужен числитель
        input += "/"
    }

    fun op(s: String) {
        if (error) clearAll()
        justEvaluated = false
        notice = null
        entryFresh = false
        when {
            // самое начало выражения: разрешаем только унарный минус
            input.isEmpty() && tokens.isEmpty() ->
                if (s == CalculatorEngine.OP_MINUS) input = "−"

            // input пуст: после оператора — унарный минус или замена оператора;
            // после числа (например после «%») — просто добавляем оператор
            input.isEmpty() -> {
                val last = tokens.last()
                when {
                    last !in CalculatorEngine.BINARY_OPS -> tokens.add(s)
                    s == CalculatorEngine.OP_MINUS && last != CalculatorEngine.OP_MINUS -> input = "−"
                    else -> tokens[tokens.lastIndex] = s
                }
            }

            // «−» без числа: превращаем предыдущий оператор в нажатый
            input == "−" ->
                if (tokens.isNotEmpty() && tokens.last() in CalculatorEngine.BINARY_OPS) {
                    tokens[tokens.lastIndex] = s
                }

            // обычный случай: фиксируем число и продолжаем цепочку выражения
            // (5 + 8 × остаётся в одной строке; «=» посчитает по приоритетам)
            else -> {
                tokens.add(input)
                tokens.add(s)
                input = ""
            }
        }
    }

    fun percent() {
        if (error) clearAll()
        justEvaluated = false
        notice = null
        entryFresh = false
        if (input.isNotEmpty()) {
            tokens.add("$input%")
            input = ""
        } else if (tokens.isNotEmpty()) {
            val last = tokens.last()
            if (!last.endsWith("%") && last !in CalculatorEngine.BINARY_OPS) {
                tokens[tokens.lastIndex] = "$last%"
            }
        }
    }

    /**
     * MU, как на Casio DJ-120D — постфиксная клавиша над текущей операцией:
     *
     *  [A] [÷] [B] [MU]  →  A ÷ (1 − B/100)     — обратные проценты / цена из маржи
     *      18 ÷ 24 MU   → 450/19 ≈ 23.68  (акция подешевела на 24% — цена вчера)
     *      14 ÷ −15 MU  → 280/23 ≈ 12.17  (цена до наценки 15%)
     *  [A] [−] [B] [MU]  →  (A − B) ÷ B × 100   — наценка в % от себестоимости
     *      1200 − 800 MU → 50 (%)
     *
     * Результат появляется сразу, без «=». Отрицательная ставка вводится клавишей «−»
     * как унарный минус. В остальных контекстах MU показывает подсказку.
     */
    fun mu() {
        if (error) clearAll()
        justEvaluated = false
        notice = null

        val bRaw = input.ifEmpty { tokens.lastOrNull() }
        val opIdx = if (input.isNotEmpty()) tokens.lastIndex else tokens.size - 2
        val op = if (opIdx >= 0) tokens.getOrNull(opIdx) else null
        val a = if (opIdx >= 1) {
            tokens.getOrNull(opIdx - 1)?.toRational()
        } else {
            null
        }
        val b = bRaw?.takeIf { !it.endsWith("%") }?.toRational()

        when {
            op == CalculatorEngine.OP_DIV && a != null && b != null -> {
                val d = Rational.ONE - b / Rational.HUNDRED
                if (d.isZero()) {
                    error = true
                    errorMessage = "деление на 0"
                } else {
                    finishMu(a / d)
                }
            }
            op == CalculatorEngine.OP_MINUS && a != null && b != null -> {
                if (b.isZero()) {
                    error = true
                    errorMessage = "деление на 0"
                } else {
                    finishMu((a - b) * Rational.HUNDRED / b)
                }
            }
            else -> notice = "MU работает после «÷» (A÷(1−B/100)) и после «−» (наценка (A−B)÷B×100)"
        }
    }

    private fun finishMu(r: Rational) {
        val shown = tokens.toList() + (if (input.isNotEmpty()) listOf(input) else emptyList())
        lastExpression = (shown + "MU").joinToString(" ")
        tokens.clear()
        input = r.displayString()
        justEvaluated = true
    }

    // ---------- память (MR / MS / M+) ----------

    /** Значение на дисплее: набираемое число либо последнее зафиксированное. */
    private fun currentValue(): Rational? {
        val s = if (input.isNotEmpty()) {
            input
        } else {
            tokens.lastOrNull()?.takeIf { it !in CalculatorEngine.BINARY_OPS && !it.endsWith("%") }
        }
        return s?.toRational()
    }

    /** MS — запомнить текущее значение (перезаписывает память). */
    fun memoryStore() {
        if (error) clearAll()
        justEvaluated = false
        notice = null
        currentValue()?.let {
            memory = it
            hasMemory = true
            entryFresh = true
        }
    }

    /** M+ — прибавить текущее значение к памяти. */
    fun memoryAdd() {
        if (error) clearAll()
        justEvaluated = false
        notice = null
        currentValue()?.let {
            memory = (if (hasMemory) memory else Rational.ZERO) + it
            hasMemory = true
            entryFresh = true
        }
    }

    /** MR — вывести память: подставляет её как текущее число. */
    fun memoryRecall() {
        if (error) clearAll()
        justEvaluated = false
        notice = null
        if (input.isEmpty() && tokens.isNotEmpty() && tokens.last() !in CalculatorEngine.BINARY_OPS) {
            tokens.removeAt(tokens.lastIndex)
        }
        input = if (hasMemory) memory.displayString() else "0"
        entryFresh = true
    }

    fun backspace() {
        if (error) {
            clearAll()
            return
        }
        justEvaluated = false
        notice = null
        entryFresh = false // правка отозванного числа — обычное редактирование
        if (input.isNotEmpty()) {
            input = input.dropLast(1)
        } else if (tokens.isNotEmpty()) {
            val last = tokens.removeAt(tokens.lastIndex)
            if (last !in CalculatorEngine.BINARY_OPS) {
                input = last.removeSuffix("%") // возвращаем число на редактирование
            }
        }
    }

    fun equalsKey() {
        if (error) return
        entryFresh = false
        val full = (tokens.toList() + (if (input.isNotEmpty()) listOf(input) else emptyList()))
            .toMutableList()
        while (full.isNotEmpty() && full.last() in CalculatorEngine.BINARY_OPS) {
            full.removeAt(full.size - 1)
        }
        if (full.isEmpty()) return

        when (val outcome = CalculatorEngine.evaluate(full)) {
            is CalculatorEngine.EvalOutcome.Failure -> {
                error = true
                errorMessage = outcome.message
                return
            }
            is CalculatorEngine.EvalOutcome.Success -> {
                lastExpression = full.joinToString(" ")
                tokens.clear()
                input = outcome.value.displayString()
                justEvaluated = true
            }
        }
    }

    fun clearAll() {
        tokens.clear()
        input = ""
        lastExpression = null
        notice = null
        error = false
        errorMessage = null
        justEvaluated = false
        entryFresh = false
        // память (memory) сознательно не трогаем: «C» очищает только вычисление
    }

    private fun prepareForTyping() {
        if (error) clearAll()
        notice = null
        if (entryFresh) {
            entryFresh = false
            input = "" // после MS/M+/MR новая цифра начинает новое число
        }
        if (justEvaluated) {
            justEvaluated = false
            lastExpression = null
            input = "" // после «=» новая цифра начинает новое число
        }
        // набор цифр после «%» заменяет «висящее» число
        if (input.isEmpty() && tokens.isNotEmpty() && tokens.last() !in CalculatorEngine.BINARY_OPS) {
            tokens.removeAt(tokens.lastIndex)
        }
    }
}
