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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panel: View
    private lateinit var btnLimpar: Button
    private lateinit var btnFechar: Button
    private lateinit var btnHistorico: Button
    private lateinit var campos: List<EditText>
    private lateinit var resultado: TextView
    private lateinit var km: EditText
    private lateinit var kmPorLitro: EditText
    private lateinit var precoLitro: EditText
    private lateinit var gasto: TextView
    private lateinit var percentual: TextView
    private lateinit var dataSelecionada: TextView
    private lateinit var diaAnterior: View
    private lateinit var diaSeguinte: View
    private lateinit var diaLivre: CheckBox

    private lateinit var banco: BancoDados
    private var expandido = false
    private var diaAtual: LocalDate = LocalDate.now()
    private var carregando = false
    private var confirmandoLimpeza = false
    private lateinit var cancelarLimpeza: Runnable

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
            // NOT_TOUCH_MODAL deixa os toques fora do balão chegarem ao app de baixo;
            // WATCH_OUTSIDE_TOUCH avisa o balão desses toques para ele se recolher.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        banco = BancoDados(this)
        panel = root.findViewById(R.id.panel)
        btnLimpar = root.findViewById(R.id.btnLimpar)
        btnFechar = root.findViewById(R.id.btnFechar)
        btnHistorico = root.findViewById(R.id.btnHistorico)
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
        percentual = root.findViewById(R.id.percentual)
        dataSelecionada = root.findViewById(R.id.dataSelecionada)
        diaAnterior = root.findViewById(R.id.diaAnterior)
        diaSeguinte = root.findViewById(R.id.diaSeguinte)
        diaLivre = root.findViewById(R.id.diaLivre)

        // Qualquer mudança em qualquer caixa grava o dia automaticamente.
        (campos + listOf(km, kmPorLitro, precoLitro)).forEach {
            it.addTextChangedListener(observador {
                if (!carregando) salvarDia()
                atualizarTudo()
            })
        }
        diaLivre.setOnCheckedChangeListener { _, _ ->
            if (!carregando) salvarDia()
            atualizarTudo()
        }
        // As setas trocam o dia editado, para lançar dias atrasados (nunca depois de hoje).
        diaAnterior.setOnClickListener { carregarDia(diaAtual.minusDays(1)) }
        diaSeguinte.setOnClickListener {
            if (diaAtual < LocalDate.now()) carregarDia(diaAtual.plusDays(1))
        }
        carregarDia()

        // Limpar pede confirmação: o 1º toque vira "Confirmar?", o 2º (em até 3 s) apaga.
        val corOriginal = btnLimpar.textColors
        cancelarLimpeza = Runnable {
            confirmandoLimpeza = false
            btnLimpar.text = "Limpar"
            btnLimpar.setTextColor(corOriginal)
        }
        btnLimpar.setOnClickListener {
            if (!confirmandoLimpeza) {
                confirmandoLimpeza = true
                btnLimpar.text = "Confirmar?"
                btnLimpar.setTextColor(0xFFC62828.toInt())
                btnLimpar.postDelayed(cancelarLimpeza, 3000)
            } else {
                btnLimpar.removeCallbacks(cancelarLimpeza)
                cancelarLimpeza.run()
                campos.forEach { it.text.clear() }
                campos.first().requestFocus()
            }
        }
        btnFechar.setOnClickListener { stopSelf() }
        btnHistorico.setOnClickListener {
            alternarPainel()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_HISTORICO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        configurarArraste(root.findViewById(R.id.bubble))

        // Tocar fora do painel aberto recolhe o balão (os valores já estão salvos).
        root.setOnTouchListener { _, evento ->
            if (evento.action == MotionEvent.ACTION_OUTSIDE && expandido) alternarPainel()
            false
        }

        windowManager.addView(root, params)
    }

    // Aberto por um lembrete ou pelo histórico: mostra o painel já no dia pedido.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_DATA)?.let { data ->
            if (!expandido) alternarPainel()
            carregarDia(LocalDate.parse(data))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::banco.isInitialized) banco.close()
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
        btnHistorico.visibility = visibilidade

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (expandido) {
            // Ao tocar no balão, o painel sempre abre no dia de hoje.
            if (diaAtual != LocalDate.now()) carregarDia()
            // Precisa receber foco para o teclado funcionar nas caixas.
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(root, params)
            root.post { manterNaTela() }
            val campo = campos.firstOrNull { it.text.isEmpty() } ?: campos.first()
            campo.requestFocus()
            campo.postDelayed({ imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT) }, 150)
        } else {
            btnLimpar.removeCallbacks(cancelarLimpeza)
            cancelarLimpeza.run()
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

    private fun atualizarTudo() {
        val bruto = somaCorrida()
        resultado.text = Formato.moeda(bruto)

        val consumo = Formato.lerValor(kmPorLitro.text.toString())
        val gastoCombustivel = if (consumo > 0) {
            Formato.lerValor(km.text.toString()) / consumo * Formato.lerValor(precoLitro.text.toString())
        } else {
            0.0
        }
        gasto.text = Formato.moeda(gastoCombustivel)
        percentual.text = Formato.percentual(gastoCombustivel, bruto)

        val hoje = LocalDate.now()
        dataSelecionada.text = when (diaAtual) {
            hoje -> "Hoje"
            hoje.minusDays(1) -> "Ontem"
            else -> diaAtual.format(formatoData)
        }
        diaSeguinte.alpha = if (diaAtual < hoje) 1f else 0.3f
        // Dia livre deixa as caixas apagadas, mas ainda editáveis.
        val alfa = if (diaLivre.isChecked) 0.4f else 1f
        (campos + listOf(km, kmPorLitro, precoLitro)).forEach { it.alpha = alfa }
    }

    private fun somaCorrida() = campos.sumOf { Formato.lerValor(it.text.toString()) }

    // Mostra o registro do dia. Um dia sem registro começa com as caixas vazias,
    // mantendo km/l e preço do litro do último dia salvo.
    private fun carregarDia(data: LocalDate = LocalDate.now()) {
        diaAtual = minOf(data, LocalDate.now())
        val dia = banco.buscar(diaAtual)
            ?: banco.ultimoDia()?.let { Dia(diaAtual, kmPorLitro = it.kmPorLitro, precoLitro = it.precoLitro) }
            ?: diaDasPreferenciasAntigas()

        carregando = true
        listOf(dia.valor1, dia.valor2, dia.valor3).forEachIndexed { i, v -> campos[i].setText(texto(v)) }
        km.setText(texto(dia.km))
        kmPorLitro.setText(texto(dia.kmPorLitro))
        precoLitro.setText(texto(dia.precoLitro))
        diaLivre.isChecked = dia.folga
        carregando = false
        atualizarTudo()
    }

    // Versões anteriores guardavam km/l e preço do litro fora do banco.
    private fun diaDasPreferenciasAntigas(): Dia {
        val prefs = getSharedPreferences("combustivel", Context.MODE_PRIVATE)
        return Dia(
            diaAtual,
            kmPorLitro = Formato.lerValor(prefs.getString("kmPorLitro", "") ?: ""),
            precoLitro = Formato.lerValor(prefs.getString("precoLitro", "") ?: "")
        )
    }

    private fun salvarDia() {
        banco.salvar(
            Dia(
                data = diaAtual,
                valor1 = Formato.lerValor(campos[0].text.toString()),
                valor2 = Formato.lerValor(campos[1].text.toString()),
                valor3 = Formato.lerValor(campos[2].text.toString()),
                km = Formato.lerValor(km.text.toString()),
                kmPorLitro = Formato.lerValor(kmPorLitro.text.toString()),
                precoLitro = Formato.lerValor(precoLitro.text.toString()),
                folga = diaLivre.isChecked
            )
        )
    }

    private fun texto(valor: Double) = if (valor == 0.0) "" else Formato.numero(valor)

    private val formatoData = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("pt-BR"))

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

    companion object {
        const val EXTRA_DATA = "data"
    }
}
