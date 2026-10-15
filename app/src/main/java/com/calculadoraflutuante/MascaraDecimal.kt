package com.calculadoraflutuante

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

// Máscara que põe a vírgula sozinha: digita-se só números e as últimas `casas`
// viram decimais. Com 1 casa, "58" vira "5,8"; com 2 casas, "465" vira "4,65".
class MascaraDecimal(
    private val campo: EditText,
    private val casas: Int,
    private val maxDigitos: Int
) : TextWatcher {

    private var formatando = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        if (formatando) return
        val digitos = s.filter { it.isDigit() }.trimStart('0').take(maxDigitos).toString()
        val novo = if (digitos.isEmpty()) "" else {
            val completo = digitos.padStart(casas + 1, '0')
            completo.dropLast(casas) + "," + completo.takeLast(casas)
        }
        if (novo != s.toString()) {
            formatando = true
            s.replace(0, s.length, novo)
            formatando = false
            campo.setSelection(novo.length)
        }
    }

    companion object {
        // Texto já no formato da máscara, para carregar um valor salvo na caixa.
        fun formatar(valor: Double, casas: Int): String =
            if (valor <= 0.0) "" else String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.${casas}f", valor)
    }
}
