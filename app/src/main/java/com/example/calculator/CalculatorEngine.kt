package com.example.calculator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

/**
 * Точное рациональное число: num/den, den > 0, всегда сокращено.
 * Арифметика без ошибок двойной точности: 0.1 + 0.2 = 3/10 (ровно 0.3).
 */
data class Rational(val num: BigInteger, val den: BigInteger) {

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)
        val HUNDRED = Rational(BigInteger.valueOf(100), BigInteger.ONE)
        private val TEN = BigInteger.TEN

        fun of(num: BigInteger, den: BigInteger): Rational? {
            if (den.signum() == 0) return null
            var n = num
            var d = den
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            val g = n.gcd(d)
            if (g.signum() != 0) {
                n = n.divide(g)
                d = d.divide(g)
            }
            return Rational(n, d)
        }

        /** Парсит "12", "−3.5", "2/5", "−450/19", "2.5/4". */
        fun parse(raw: String): Rational? {
            if (raw.isEmpty()) return null
            val s = raw.replace('−', '-')
            val parts = s.split('/')
            return when (parts.size) {
                1 -> fromDecimal(s)
                2 -> {
                    val n = fromDecimal(parts[0]) ?: return null
                    val d = fromDecimal(parts[1]) ?: return null
                    if (d.isZero()) return null
                    n / d
                }
                else -> null
            }
        }

        private fun fromDecimal(s: String): Rational? {
            if (s.isEmpty()) return null
            return try {
                val bd = BigDecimal(s)
                of(bd.unscaledValue(), TEN.pow(bd.scale()))
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    operator fun plus(o: Rational): Rational = of(num * o.den + den * o.num, den * o.den)!!
    operator fun minus(o: Rational): Rational = of(num * o.den - den * o.num, den * o.den)!!
    operator fun times(o: Rational): Rational = of(num * o.num, den * o.den)!!
    operator fun div(o: Rational): Rational {
        require(!o.isZero()) { "division by zero" }
        return of(num * o.den, den * o.num)!!
    }

    fun isZero(): Boolean = num.signum() == 0

    /** "3" либо "11/15"; минус — типографский «−». */
    fun displayString(): String {
        val sign = if (num.signum() < 0) "−" else ""
        val body = num.abs().toString() + (if (den == BigInteger.ONE) "" else "/$den")
        return sign + body
    }

    /**
     * Десятичная подсказка для дроби: "≈ 0.333333333333" либо точная "= 0.25".
     * null для целых чисел.
     */
    fun decimalHint(): String? {
        if (den == BigInteger.ONE) return null
        var d = den
        var twos = 0
        var fives = 0
        while (d.and(BigInteger.ONE).signum() == 0) {
            d = d.shiftRight(1)
            twos++
        }
        val five = BigInteger.valueOf(5)
        while (d.rem(five).signum() == 0) {
            d = d.divide(five)
            fives++
        }
        val exact = d.signum() == 1 && d.compareTo(BigInteger.ONE) == 0
        val scale = max(twos, fives)
        return (if (exact && scale <= 14) {
            "= " + BigDecimal(num)
                .divide(BigDecimal(den), scale, RoundingMode.UNNECESSARY)
                .toPlainString()
        } else {
            "≈ " + BigDecimal(num)
                .divide(BigDecimal(den), MathContext(12))
                .stripTrailingZeros()
                .toPlainString()
        }).replace('-', '−')
    }
}

fun String.toRational(): Rational? = Rational.parse(this)

/**
 * Движок калькулятора: точная рациональная арифметика.
 *
 * Токены — строки:
 *  - числа: "12", "−3.5", "2/5", "−450/19"
 *  - проценты: "15%"
 *  - операторы: "+" "−" "×" "÷"
 *
 * Проценты (как на обычных калькуляторах):
 *  - после «+» или «−»: p% берётся от суммы слева → 200 + 10% = 220, 800 + 50% = 1200
 *  - после «×», «÷» или в начале выражения: p% = p/100 → 200 × 10% = 20, 200 ÷ 10% = 2000
 *
 * MU здесь не считается (постфиксная клавиша уровня UI, см. CalcState.mu()).
 */
object CalculatorEngine {

    const val OP_PLUS = "+"
    const val OP_MINUS = "−" // U+2212
    const val OP_MUL = "×"
    const val OP_DIV = "÷"

    val BINARY_OPS = setOf(OP_PLUS, OP_MINUS, OP_MUL, OP_DIV)

    /** Результат вычисления: успех либо ошибка с пояснением (message = null → общая «Ошибка»). */
    sealed interface EvalOutcome {
        data class Success(val value: Rational) : EvalOutcome
        data class Failure(val message: String?) : EvalOutcome
    }

    /** Подсказка-десятичное для набранного числа с дробью ("2/5" → "= 0.4"). */
    fun decimalHint(raw: String): String? = raw.toRational()?.decimalHint()

    fun evaluate(tokens: List<String>): EvalOutcome {
        // диагностика частых ошибок по сырым токенам
        for (i in 0 until tokens.size - 1) {
            val op = tokens[i]
            val b = tokens.getOrNull(i + 1)?.toRational()
            if (op == OP_DIV && b?.isZero() == true) {
                return EvalOutcome.Failure("деление на 0")
            }
        }
        val r = internalEvaluate(tokens)
        return if (r != null) EvalOutcome.Success(r) else EvalOutcome.Failure(null)
    }

    private fun internalEvaluate(tokens: List<String>): Rational? {
        if (tokens.isEmpty()) return null
        val work = tokens.toMutableList()
        resolvePercents(work)

        // 1. умножение и деление (приоритет выше, чем у + и −)
        val stack = mutableListOf<String>()
        var i = 0
        while (i < work.size) {
            val t = work[i]
            if (t == OP_MUL || t == OP_DIV) {
                val a = stack.lastOrNull()?.toRational() ?: return null
                val b = work.getOrNull(i + 1)?.toRational() ?: return null
                stack.removeAt(stack.size - 1)
                val r = if (t == OP_MUL) {
                    a * b
                } else {
                    if (b.isZero()) return null
                    a / b
                }
                stack.add(r.displayString())
                i += 2
            } else {
                stack.add(t)
                i += 1
            }
        }
        if (stack.size % 2 == 0) return null

        // 2. сложение и вычитание слева направо
        var acc = stack[0].toRational() ?: return null
        var j = 1
        while (j < stack.size) {
            val op = stack[j]
            val b = stack.getOrNull(j + 1)?.toRational() ?: return null
            acc = when (op) {
                OP_PLUS -> acc + b
                OP_MINUS -> acc - b
                else -> return null
            }
            j += 2
        }
        return acc
    }

    /**
     * Заменяет токены «p%» на числа.
     * «p%» после «+»/«−» — это p% от вычисленного левого операнда; иначе — просто p/100.
     */
    private fun resolvePercents(tokens: MutableList<String>) {
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length > 1 && t.endsWith("%")) {
                val p = t.dropLast(1).toRational() ?: return
                val v = if (i > 0 && (tokens[i - 1] == OP_PLUS || tokens[i - 1] == OP_MINUS)) {
                    val left = internalEvaluate(tokens.subList(0, i - 1)) ?: return
                    left * p / Rational.HUNDRED
                } else {
                    p / Rational.HUNDRED
                }
                tokens[i] = v.displayString()
            }
            i++
        }
    }
}
