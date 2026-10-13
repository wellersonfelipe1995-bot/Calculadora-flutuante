package com.calculadoraflutuante

import java.text.NumberFormat
import java.util.Locale

object Formato {
    private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    private val moeda = NumberFormat.getCurrencyInstance(ptBR)

    fun moeda(valor: Double): String = moeda.format(valor)

    // Número para mostrar numa caixa: "12,5", "120", "6,19".
    fun numero(valor: Double): String =
        NumberFormat.getNumberInstance(ptBR).apply {
            isGroupingUsed = false
            maximumFractionDigits = 2
        }.format(valor)

    fun percentual(parte: Double, total: Double): String =
        if (total > 0) String.format(ptBR, "%.1f%%", parte / total * 100) else "0%"

    // Aceita "12,50", "12.50" e "1.234,56".
    fun lerValor(texto: String): Double {
        val limpo = texto.trim()
        if (limpo.isEmpty()) return 0.0
        val normalizado = if (limpo.contains(',')) {
            limpo.replace(".", "").replace(',', '.')
        } else {
            limpo
        }
        return normalizado.toDoubleOrNull() ?: 0.0
    }
}
