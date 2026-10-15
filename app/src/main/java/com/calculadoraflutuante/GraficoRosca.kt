package com.calculadoraflutuante

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

// Gráfico de rosca desenhado à mão: uma fatia por valor e o total no meio.
class GraficoRosca @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var valores: List<Double> = emptyList()
    private var cores: List<Int> = emptyList()
    private var textoCentro = ""
    private var legendaCentro = ""

    private val densidade = resources.displayMetrics.density
    private val area = RectF()

    private val pincelFatia = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val pincelTrilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.trilho_grafico)
    }
    private val pincelTotal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.texto)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pincelLegenda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.texto_secundario)
        textAlign = Paint.Align.CENTER
    }

    fun definir(valores: List<Double>, cores: List<Int>, textoCentro: String, legendaCentro: String) {
        this.valores = valores
        this.cores = cores
        this.textoCentro = textoCentro
        this.legendaCentro = legendaCentro
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lado = minOf(width, height).toFloat()
        val espessura = lado * 0.16f
        val raio = (lado - espessura) / 2f
        val cx = width / 2f
        val cy = height / 2f
        area.set(cx - raio, cy - raio, cx + raio, cy + raio)
        pincelFatia.strokeWidth = espessura
        pincelTrilho.strokeWidth = espessura

        canvas.drawArc(area, 0f, 360f, false, pincelTrilho)

        val total = valores.sum()
        if (total > 0) {
            val fatias = valores.count { it > 0 }
            // Pequena folga entre as fatias, só quando há mais de uma.
            val folga = if (fatias > 1) 2f else 0f
            var inicio = -90f
            valores.forEachIndexed { i, valor ->
                if (valor <= 0) return@forEachIndexed
                val angulo = (valor / total * 360f).toFloat()
                pincelFatia.color = cores[i]
                canvas.drawArc(area, inicio + folga / 2, (angulo - folga).coerceAtLeast(0.5f), false, pincelFatia)
                inicio += angulo
            }
        }

        // Total no centro, encolhendo a fonte se não couber no furo da rosca.
        val larguraFuro = (raio - espessura / 2f) * 2f * 0.86f
        pincelTotal.textSize = 15f * densidade
        while (pincelTotal.measureText(textoCentro) > larguraFuro && pincelTotal.textSize > 8f * densidade) {
            pincelTotal.textSize -= densidade
        }
        pincelLegenda.textSize = 11f * densidade
        val base = cy + pincelTotal.textSize * 0.15f
        canvas.drawText(textoCentro, cx, base, pincelTotal)
        canvas.drawText(legendaCentro, cx, base + pincelLegenda.textSize * 1.3f, pincelLegenda)
    }
}
