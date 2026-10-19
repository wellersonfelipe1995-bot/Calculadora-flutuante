package com.calculadoraflutuante

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.InputFilter
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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

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
    private lateinit var botaoCompartilhar: View
    private lateinit var acoesData: View
    private lateinit var liquidoImagem: TextView
    private lateinit var linhaAbastecer: View
    private lateinit var btnAbasteci: View
    private lateinit var btnDesfazer: View
    private lateinit var infoAbastecido: TextView
    // Trechos do dia já fechados com "Abasteci" (antes de trocar km/l e preço).
    private var kmAnterior = 0.0
    private var gastoAnterior = 0.0

    private lateinit var lixeira: ImageView
    private lateinit var banco: BancoDados
    private var expandido = false
    // Posição do balão antes de abrir o painel, para voltar a ela ao fechar.
    // Fica nula se o usuário arrastar o painel aberto (aí ele fica onde foi solto).
    private var xAntesDeAbrir: Int? = null
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
        botaoCompartilhar = root.findViewById(R.id.compartilhar)
        acoesData = root.findViewById(R.id.acoesData)
        liquidoImagem = root.findViewById(R.id.liquidoImagem)
        linhaAbastecer = root.findViewById(R.id.linhaAbastecer)
        btnAbasteci = root.findViewById(R.id.btnAbasteci)
        btnDesfazer = root.findViewById(R.id.btnDesfazer)
        infoAbastecido = root.findViewById(R.id.infoAbastecido)
        btnAbasteci.setOnClickListener { abastecer() }
        btnDesfazer.setOnClickListener { desfazerAbastecimento() }
        botaoCompartilhar.setOnClickListener { compartilhar() }

        // km/l e preço do litro: só números, a vírgula entra sozinha (58 → 5,8; 465 → 4,65).
        kmPorLitro.addTextChangedListener(MascaraDecimal(kmPorLitro, casas = 1, maxDigitos = 2))
        // km: só números inteiros, até 4 dígitos (9999 km).
        km.filters = arrayOf(InputFilter { origem, inicio, fim, _, _, _ ->
            val digitos = origem.subSequence(inicio, fim).filter { it.isDigit() }
            if (digitos.length == fim - inicio) null else digitos
        }, InputFilter.LengthFilter(4))
        precoLitro.addTextChangedListener(MascaraDecimal(precoLitro, casas = 2, maxDigitos = 4))

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
                Intent(this, HistoricoActivity::class.java)
                    .putExtra(HistoricoActivity.EXTRA_HISTORICO, true)
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
        criarLixeira()
    }

    // Lixeira no pé da tela: aparece ao arrastar o balão; soltar em cima dela fecha o balão.
    private fun criarLixeira() {
        val dp = resources.displayMetrics.density
        lixeira = ImageView(this).apply {
            setImageResource(R.drawable.ic_lixeira)
            setBackgroundResource(R.drawable.lixeira_bg)
            scaleType = ImageView.ScaleType.CENTER
            visibility = View.GONE
        }
        val tamanho = (64 * dp).toInt()
        windowManager.addView(lixeira, WindowManager.LayoutParams(
            tamanho,
            tamanho,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * dp).toInt()
        })
    }

    private fun dedoSobreLixeira(x: Float, y: Float): Boolean {
        val pos = IntArray(2)
        lixeira.getLocationOnScreen(pos)
        val centroX = pos[0] + lixeira.width / 2f
        val centroY = pos[1] + lixeira.height / 2f
        return hypot(x - centroX, y - centroY) < 72 * resources.displayMetrics.density
    }

    private fun destacarLixeira(ativa: Boolean) {
        lixeira.setBackgroundResource(if (ativa) R.drawable.lixeira_ativa_bg else R.drawable.lixeira_bg)
        val escala = if (ativa) 1.25f else 1f
        lixeira.animate().scaleX(escala).scaleY(escala).setDuration(100).start()
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
        if (::lixeira.isInitialized && lixeira.isAttachedToWindow) {
            windowManager.removeView(lixeira)
        }
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
        var sobreLixeira = false
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
                    if (!arrastou && (abs(dx) > limiar || abs(dy) > limiar)) {
                        arrastou = true
                        lixeira.visibility = View.VISIBLE
                    }
                    if (arrastou) {
                        xAntesDeAbrir = null
                        params.x = (inicioX + dx).toInt()
                        params.y = (inicioY + dy).toInt()
                        windowManager.updateViewLayout(root, params)

                        val agora = dedoSobreLixeira(evento.rawX, evento.rawY)
                        if (agora != sobreLixeira) {
                            sobreLixeira = agora
                            destacarLixeira(agora)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (arrastou) {
                        lixeira.visibility = View.GONE
                        if (sobreLixeira) {
                            sobreLixeira = false
                            destacarLixeira(false)
                            if (evento.action == MotionEvent.ACTION_UP) stopSelf()
                        }
                    } else if (evento.action == MotionEvent.ACTION_UP) {
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
            xAntesDeAbrir = params.x
            root.post { centralizar() }
            val campo = campos.firstOrNull { it.text.isEmpty() } ?: campos.first()
            campo.requestFocus()
            campo.postDelayed({ imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT) }, 150)
        } else {
            btnLimpar.removeCallbacks(cancelarLimpeza)
            cancelarLimpeza.run()
            imm.hideSoftInputFromWindow(root.windowToken, 0)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            xAntesDeAbrir?.let { params.x = it }
            xAntesDeAbrir = null
            windowManager.updateViewLayout(root, params)
        }
    }

    // Painel aberto fica centralizado na largura da tela.
    private fun centralizar() {
        if (!expandido) return
        val larguraTela = resources.displayMetrics.widthPixels
        params.x = ((larguraTela - root.width) / 2).coerceAtLeast(0)
        windowManager.updateViewLayout(root, params)
    }

    private fun observador(aoMudar: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = aoMudar()
    }

    private fun atualizarTudo() {
        val bruto = somaCorrida()
        resultado.text = Formato.moeda(bruto)

        // Gasto do dia = trechos já fechados no "Abasteci" + trecho atual.
        val gastoCombustivel = gastoAnterior + gastoTrechoAtual()
        gasto.text = Formato.moeda(gastoCombustivel)
        liquidoImagem.text = "Líquido ${Formato.moeda(bruto - gastoCombustivel)}"
        percentual.text = Formato.percentual(gastoCombustivel, bruto)
        if (kmAnterior > 0) {
            infoAbastecido.text = "+ ${Formato.moeda(gastoAnterior)} de ${"%.0f".format(kmAnterior)} km antes de abastecer"
            btnDesfazer.visibility = View.VISIBLE
        } else {
            infoAbastecido.text = ""
            btnDesfazer.visibility = View.GONE
        }

        val hoje = LocalDate.now()
        dataSelecionada.text = when (diaAtual) {
            hoje -> "Hoje"
            hoje.minusDays(1) -> "Ontem"
            else -> diaAtual.format(formatoData)
        }
        diaSeguinte.alpha = if (diaAtual < hoje) 1f else 0.3f
        // Com ganhos ou km lançados não faz sentido marcar dia livre: a opção fica
        // desabilitada (mas, se já estiver marcada, continua dando para desmarcar).
        val temValores = bruto > 0 || kmAnterior > 0 || Formato.lerValor(km.text.toString()) > 0
        diaLivre.isEnabled = !temValores || diaLivre.isChecked
        diaLivre.alpha = if (diaLivre.isEnabled) 1f else 0.35f
        // Dia livre bloqueia as caixas: não dá para lançar valores num dia de folga.
        val livre = diaLivre.isChecked
        (campos + listOf(km, kmPorLitro, precoLitro)).forEach {
            it.isEnabled = !livre
            it.alpha = if (livre) 0.4f else 1f
            if (livre && it.hasFocus()) it.clearFocus()
        }
        btnAbasteci.isEnabled = !livre
        btnAbasteci.alpha = if (livre) 0.4f else 1f
    }

    private fun gastoTrechoAtual(): Double {
        val consumo = Formato.lerValor(kmPorLitro.text.toString())
        return if (consumo > 0) {
            Formato.lerValor(km.text.toString()) / consumo * Formato.lerValor(precoLitro.text.toString())
        } else {
            0.0
        }
    }

    // Fecha o trecho atual (km e gasto com o km/l e preço de antes) e zera o km para o
    // próximo trecho; km/l e preço ficam como estão para serem trocados pelos novos.
    private fun abastecer() {
        val kmTrecho = Formato.lerValor(km.text.toString())
        if (kmTrecho <= 0) {
            Toast.makeText(this, "Lance o km rodado até agora antes de tocar em Abasteci", Toast.LENGTH_LONG).show()
            return
        }
        val gastoTrecho = gastoTrechoAtual()
        kmAnterior += kmTrecho
        gastoAnterior += gastoTrecho
        km.setText("")  // salva o dia e atualiza a tela
        kmPorLitro.requestFocus()
        kmPorLitro.selectAll()
        Toast.makeText(
            this,
            "Trecho guardado: ${"%.0f".format(kmTrecho)} km · ${Formato.moeda(gastoTrecho)}. Coloque o km/l e o preço novos.",
            Toast.LENGTH_LONG
        ).show()
    }

    // Junta de volta os trechos guardados no km atual (com o km/l e preço atuais).
    private fun desfazerAbastecimento() {
        val kmJunto = kmAnterior + Formato.lerValor(km.text.toString())
        kmAnterior = 0.0
        gastoAnterior = 0.0
        km.setText(textoKm(kmJunto))  // salva o dia e atualiza a tela
        Toast.makeText(this, "Abastecimento desfeito: km somados de volta numa linha só", Toast.LENGTH_SHORT).show()
    }

    private fun somaCorrida() = campos.sumOf { Formato.lerValor(it.text.toString()) }

    // Mostra o registro do dia. Um dia sem registro começa com as caixas vazias,
    // mantendo km/l e preço do litro do último dia salvo.
    private fun carregarDia(data: LocalDate = LocalDate.now()) {
        diaAtual = minOf(data, LocalDate.now())
        val dia = banco.buscar(diaAtual)
            ?: banco.ultimoDia()?.let { Dia(diaAtual, kmPorLitro = it.kmPorLitro, precoLitro = it.precoLitro) }
            ?: diaDasPreferenciasAntigas()

        kmAnterior = dia.kmAnterior
        gastoAnterior = dia.gastoAnterior
        carregando = true
        listOf(dia.valor1, dia.valor2, dia.valor3).forEachIndexed { i, v -> campos[i].setText(texto(v)) }
        km.setText(textoKm(dia.km))
        kmPorLitro.setText(MascaraDecimal.formatar(dia.kmPorLitro, 1))
        precoLitro.setText(MascaraDecimal.formatar(dia.precoLitro, 2))
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
                folga = diaLivre.isChecked,
                kmAnterior = kmAnterior,
                gastoAnterior = gastoAnterior
            )
        )
    }

    private fun textoKm(valor: Double) = if (valor <= 0.0) "" else "%.0f".format(valor)

    private fun texto(valor: Double) = if (valor == 0.0) "" else Formato.numero(valor)

    private val formatoData = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.forLanguageTag("pt-BR"))

    // Tira uma "foto" do card com os valores do momento e abre o compartilhamento do Android.
    private fun compartilhar() {
        // Na imagem: data de verdade no lugar de "Hoje", sem setas, botão e cursor.
        val textoData = dataSelecionada.text
        dataSelecionada.text = diaAtual.format(formatoData).replaceFirstChar { it.uppercase() }
        // "Dia livre" e o botão não interessam a quem recebe: no lugar deles vai o líquido.
        val escondidos = listOf(diaAnterior, diaSeguinte, acoesData, btnAbasteci, btnDesfazer)
        liquidoImagem.visibility = View.VISIBLE
        val visibilidades = escondidos.map { it.visibility }
        escondidos.forEach { it.visibility = View.INVISIBLE }
        val caixas = campos + listOf(km, kmPorLitro, precoLitro)
        caixas.forEach { it.isCursorVisible = false }

        val cheia = Bitmap.createBitmap(panel.width, panel.height, Bitmap.Config.ARGB_8888)
        Canvas(cheia).apply {
            drawColor(0xFF212121.toInt())
            panel.draw(this)
        }
        // Sem trecho de abastecimento, a linha do "Abasteci" ficaria vazia: corta fora.
        val imagem = if (kmAnterior > 0) cheia else {
            val altura = (linhaAbastecer.top + 4 * resources.displayMetrics.density).toInt().coerceAtMost(cheia.height)
            Bitmap.createBitmap(cheia, 0, 0, cheia.width, altura).also { if (it !== cheia) cheia.recycle() }
        }

        dataSelecionada.text = textoData
        escondidos.zip(visibilidades).forEach { (v, vis) -> v.visibility = vis }
        liquidoImagem.visibility = View.INVISIBLE
        caixas.forEach { it.isCursorVisible = true }

        val nome = "meus-ganhos.png"
        File(ImagemProvider.pasta(cacheDir), nome).outputStream().use {
            imagem.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        imagem.recycle()

        // Recolhe o painel para a lista de compartilhamento aparecer sem nada por cima.
        if (expandido) alternarPainel()

        val uri = ImagemProvider.uri(nome)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(envio, "Compartilhar ganhos")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun iniciarEmPrimeiroPlano() {
        val canalId = "balao"
        val gerenciador = getSystemService(NotificationManager::class.java)
        gerenciador.createNotificationChannel(
            NotificationChannel(canalId, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN)
        )

        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, HistoricoActivity::class.java).putExtra(HistoricoActivity.EXTRA_HISTORICO, true),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificacao = Notification.Builder(this, canalId)
            .setContentTitle(getString(R.string.notificacao_titulo))
            .setContentText(getString(R.string.notificacao_texto))
            .setSmallIcon(R.drawable.ic_notificacao)
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
