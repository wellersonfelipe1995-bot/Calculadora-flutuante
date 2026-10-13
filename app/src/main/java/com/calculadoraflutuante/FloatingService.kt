package com.calculadoraflutuante

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panel: View
    private lateinit var btnLimpar: Button
    private lateinit var btnFechar: Button
    private lateinit var campos: List<EditText>
    private lateinit var resultado: TextView
    private lateinit var km: EditText
    private lateinit var kmPorLitro: EditText
    private lateinit var precoLitro: EditText
    private lateinit var gasto: TextView

    private val moeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    private var expandido = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        iniciarEmPrimeiroPlano()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LayoutInflater.from(this).inflate(R.layout.floating_view, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        panel = root.findViewById(R.id.panel)
        btnLimpar = root.findViewById(R.id.btnLimpar)
        btnFechar = root.findViewById(R.id.btnFechar)
        resultado = root.findViewById(R.id.resultado)
        campos = listOf(
            root.findViewById(R.id.valor1),
            root.findViewById(R.id.valor2),
            root.findViewById(R.id.valor3)
        )

        km = root.findViewById(R.id.km)
        kmPorLitro = root.findViewById(R.id.kmPorLitro)
        precoLitro = root.findViewById(R.id.precoLitro)
        gasto = root.findViewById(R.id.gasto)

        campos.forEach { it.addTextChangedListener(observador { atualizarResultado() }) }

        // Os dados de combustível ficam salvos entre um uso e outro do balão.
        val prefs = getSharedPreferences("combustivel", Context.MODE_PRIVATE)
        listOf(km to "km", kmPorLitro to "kmPorLitro", precoLitro to "precoLitro").forEach { (campo, chave) ->
            campo.setText(prefs.getString(chave, ""))
            campo.addTextChangedListener(observador {
                prefs.edit().putString(chave, campo.text.toString()).apply()
                atualizarGasto()
            })
        }
        atualizarGasto()

        btnLimpar.setOnClickListener {
            campos.forEach { it.text.clear() }
            campos.first().requestFocus()
        }
        btnFechar.setOnClickListener { stopSelf() }

        configurarArraste(root.findViewById(R.id.bubble))

        windowManager.addView(root, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::root.isInitialized && root.isAttachedToWindow) {
            windowManager.removeView(root)
        }
    }

    private fun configurarArraste(bolha: View) {
        var inicioX = 0
        var inicioY = 0
        var toqueX = 0f
        var toqueY = 0f
        var arrastou = false
        val limiar = 10 * resources.displayMetrics.density

        bolha.setOnTouchListener { v, evento ->
            when (evento.action) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = params.x
                    inicioY = params.y
                    toqueX = evento.rawX
                    toqueY = evento.rawY
                    arrastou = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = evento.rawX - toqueX
                    val dy = evento.rawY - toqueY
                    if (abs(dx) > limiar || abs(dy) > limiar) arrastou = true
                    if (arrastou) {
                        params.x = (inicioX + dx).toInt()
                        params.y = (inicioY + dy).toInt()
                        windowManager.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!arrastou) {
                        v.performClick()
                        alternarPainel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun alternarPainel() {
        expandido = !expandido
        val visibilidade = if (expandido) View.VISIBLE else View.GONE
        panel.visibility = visibilidade
        btnLimpar.visibility = visibilidade
        btnFechar.visibility = visibilidade

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (expandido) {
            // Precisa receber foco para o teclado funcionar nas caixas.
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(root, params)
            root.post { manterNaTela() }
            val campo = campos.firstOrNull { it.text.isEmpty() } ?: campos.first()
            campo.requestFocus()
            campo.postDelayed({ imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT) }, 150)
        } else {
            imm.hideSoftInputFromWindow(root.windowToken, 0)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(root, params)
        }
    }

    // Evita que o painel aberto fique cortado na borda direita da tela.
    private fun manterNaTela() {
        val larguraTela = resources.displayMetrics.widthPixels
        val maxX = (larguraTela - root.width).coerceAtLeast(0)
        if (params.x > maxX || params.x < 0) {
            params.x = params.x.coerceIn(0, maxX)
            windowManager.updateViewLayout(root, params)
        }
    }

    private fun observador(aoMudar: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = aoMudar()
    }

    private fun atualizarGasto() {
        val consumo = lerValor(kmPorLitro.text.toString())
        val total = if (consumo > 0) {
            lerValor(km.text.toString()) / consumo * lerValor(precoLitro.text.toString())
        } else {
            0.0
        }
        gasto.text = moeda.format(total)
    }

    private fun atualizarResultado() {
        val total = campos.sumOf { lerValor(it.text.toString()) }
        resultado.text = moeda.format(total)
    }

    // Aceita "12,50", "12.50" e "1.234,56".
    private fun lerValor(texto: String): Double {
        val limpo = texto.trim()
        if (limpo.isEmpty()) return 0.0
        val normalizado = if (limpo.contains(',')) {
            limpo.replace(".", "").replace(',', '.')
        } else {
            limpo
        }
        return normalizado.toDoubleOrNull() ?: 0.0
    }

    private fun iniciarEmPrimeiroPlano() {
        val canalId = "balao"
        val gerenciador = getSystemService(NotificationManager::class.java)
        gerenciador.createNotificationChannel(
            NotificationChannel(canalId, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN)
        )

        val abrirApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notificacao = Notification.Builder(this, canalId)
            .setContentTitle(getString(R.string.notificacao_titulo))
            .setContentText(getString(R.string.notificacao_texto))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notificacao)
        }
    }
}
