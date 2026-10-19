package com.calculadoraflutuante

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoricoActivity : Activity() {

    private enum class Aba(val titulo: String, val conteudo: Int, val botao: Int) {
        INICIO("Meus Ganhos", R.id.abaInicio, R.id.botaoInicio),
        HISTORICO("Histórico", R.id.abaHistorico, R.id.botaoHistorico),
        DESEMPENHO("Desempenho", R.id.abaDesempenho, R.id.botaoDesempenho),
        AJUSTES("Ajustes", R.id.abaAjustes, R.id.botaoAjustes)
    }

    private lateinit var banco: BancoDados
    private var abaAtual = Aba.INICIO
    private val ptBR = Locale.forLanguageTag("pt-BR")
    private val formatoData = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", ptBR)
    private val formatoMes = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        banco = BancoDados(this)
        respeitarBarrasDoSistema()
        Lembretes.agendar(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        Aba.values().forEach { aba -> findViewById<View>(aba.botao).setOnClickListener { selecionarAba(aba) } }
        findViewById<View>(R.id.verHistorico).setOnClickListener { selecionarAba(Aba.HISTORICO) }

        findViewById<Button>(R.id.btnPermissao).setOnClickListener { abrirPermissaoSobreposicao() }
        findViewById<Button>(R.id.btnIniciar).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, FloatingService::class.java))
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, R.string.sem_permissao, Toast.LENGTH_LONG).show()
                abrirPermissaoSobreposicao()
            }
        }
        findViewById<Button>(R.id.btnParar).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
        }

        // Sem a permissão do balão, a primeira coisa a fazer está em Ajustes.
        val salva = savedInstanceState?.getString(ESTADO_ABA)?.let { Aba.valueOf(it) }
        selecionarAba(salva ?: if (Settings.canDrawOverlays(this)) Aba.INICIO else Aba.AJUSTES)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ESTADO_ABA, abaAtual.name)
    }

    override fun onResume() {
        super.onResume()
        // Aberto pelo botão Histórico do balão ou pela notificação.
        if (intent.getBooleanExtra(EXTRA_HISTORICO, false)) {
            intent.removeExtra(EXTRA_HISTORICO)
            selecionarAba(Aba.HISTORICO)
        }
        mostrarHistorico()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::banco.isInitialized) banco.close()
    }

    // No Android 15 a tela ocupa até atrás da barra de status e da barra de navegação;
    // o espaçamento evita que o conteúdo e as abas fiquem escondidos atrás delas.
    private fun respeitarBarrasDoSistema() {
        findViewById<View>(R.id.raiz).setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val barras = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        // Vindo de um lembrete: abre o balão no dia que ficou sem registro.
        intent.getStringExtra(EXTRA_EDITAR_DATA)?.let { data ->
            intent.removeExtra(EXTRA_EDITAR_DATA)
            abrirBalaoNoDia(data)
        }
    }

    private fun selecionarAba(aba: Aba) {
        abaAtual = aba
        findViewById<TextView>(R.id.tituloAba).text = aba.titulo
        val verde = getColor(R.color.verde)
        val cinza = getColor(R.color.aba_inativa)
        Aba.values().forEach { a ->
            val ativa = a == aba
            findViewById<View>(a.conteudo).visibility = if (ativa) View.VISIBLE else View.GONE
            val botao = findViewById<ViewGroup>(a.botao)
            val cor = if (ativa) verde else cinza
            (botao.getChildAt(0) as ImageView).setColorFilter(cor)
            (botao.getChildAt(1) as TextView).setTextColor(cor)
        }
        if (aba == Aba.HISTORICO) findViewById<View>(R.id.abaHistorico).scrollTo(0, 0)
    }

    private fun abrirBalaoNoDia(data: String) {
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(
                Intent(this, FloatingService::class.java).putExtra(FloatingService.EXTRA_DATA, data)
            )
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, R.string.sem_permissao, Toast.LENGTH_LONG).show()
            abrirPermissaoSobreposicao()
        }
    }

    // Preenche as abas Início, Histórico e Desempenho a partir do banco.
    private fun mostrarHistorico() {
        val dias = banco.todosOsDias().filter { it.registrado }
        val porMes = dias.groupBy { YearMonth.from(it.data) }

        // Início: resumo do mês atual e os últimos dias.
        val mesAtual = YearMonth.now()
        findViewById<ViewGroup>(R.id.resumoMesAtual).apply {
            removeAllViews()
            addView(cardResumoMes(this, mesAtual, porMes[mesAtual].orEmpty()))
        }
        findViewById<ViewGroup>(R.id.ultimosDias).apply {
            removeAllViews()
            if (dias.isEmpty()) addView(textoVazio())
            dias.take(5).forEach { addView(cardDia(this, it)) }
        }
        findViewById<View>(R.id.verHistorico).visibility = if (dias.size > 5) View.VISIBLE else View.GONE

        // Histórico: todos os dias, separados por mês.
        findViewById<ViewGroup>(R.id.historico).apply {
            removeAllViews()
            if (dias.isEmpty()) addView(textoVazio())
            porMes.forEach { (mes, diasDoMes) ->
                addView(tituloMes(mes, diasDoMes))
                diasDoMes.forEach { addView(cardDia(this, it)) }
            }
        }

        // Desempenho: o card de resumo de cada mês.
        findViewById<ViewGroup>(R.id.desempenho).apply {
            removeAllViews()
            if (dias.isEmpty()) addView(textoVazio())
            porMes.forEach { (mes, diasDoMes) ->
                addView(cardResumoMes(this, mes, diasDoMes))
                addView(cardGraficoApps(this, diasDoMes))
            }
        }
    }

    private fun cardResumoMes(pai: ViewGroup, mes: YearMonth, diasDoMes: List<Dia>): View {
        val card = layoutInflater.inflate(R.layout.item_resumo_mes, pai, false)
        val bruto = diasDoMes.sumOf { it.bruto }
        val gasto = diasDoMes.sumOf { it.gasto }
        val trabalhados = diasDoMes.count { !it.folga && it.bruto > 0 }
        val livres = diasDoMes.count { it.folga }

        card.texto(R.id.mes, mes.format(formatoMes).replaceFirstChar { it.uppercase() })
        card.texto(R.id.liquido, Formato.moeda(bruto - gasto))
        card.texto(R.id.bruto, Formato.moeda(bruto))
        card.texto(R.id.rotuloCombustivel, "Combustível · ${Formato.percentual(gasto, bruto)}")
        card.texto(R.id.combustivel, Formato.moeda(gasto))
        card.texto(R.id.media, if (trabalhados > 0) Formato.moeda((bruto - gasto) / trabalhados) else "—")
        // Média do mês por km: só entram os dias com km lançado, senão o bruto
        // de um dia sem km inflaria o resultado.
        val comKm = diasDoMes.filter { it.kmTotal > 0 }
        val kmTotal = comKm.sumOf { it.kmTotal }
        card.texto(R.id.ganhoPorKm, if (kmTotal > 0) "${Formato.moeda(comKm.sumOf { it.bruto } / kmTotal)}/km" else "—")
        card.texto(R.id.kmMes, if (kmTotal > 0) "${"%.0f".format(kmTotal)} km" else "—")
        card.texto(R.id.kmPorDia, if (comKm.isNotEmpty()) "${"%.0f".format(kmTotal / comKm.size)} km" else "—")
        card.texto(R.id.dias, "${plural(trabalhados, "dia trabalhado", "dias trabalhados")} · " +
            plural(livres, "dia livre", "dias livres"))
        return card
    }

    // Rosca com quanto veio de cada fonte: 1ª caixa (Urbano Norte), 2ª (99) e 3ª (por fora).
    private fun cardGraficoApps(pai: ViewGroup, diasDoMes: List<Dia>): View {
        val card = layoutInflater.inflate(R.layout.item_grafico_apps, pai, false)
        val fontes = listOf(
            Triple("Urbano Norte", diasDoMes.sumOf { it.valor1 }, getColor(R.color.app_urbano)),
            Triple("99", diasDoMes.sumOf { it.valor2 }, getColor(R.color.app_99)),
            Triple("Por fora", diasDoMes.sumOf { it.valor3 }, getColor(R.color.app_por_fora))
        )
        val total = fontes.sumOf { it.second }

        card.findViewById<GraficoRosca>(R.id.rosca).definir(
            valores = fontes.map { it.second },
            cores = fontes.map { it.third },
            textoCentro = Formato.moeda(total),
            legendaCentro = "bruto"
        )

        listOf(R.id.legendaUrbano, R.id.legenda99, R.id.legendaPorFora).zip(fontes).forEach { (id, fonte) ->
            val (nome, valor, cor) = fonte
            val linha = card.findViewById<View>(id)
            linha.findViewById<View>(R.id.ponto).background =
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(cor) }
            linha.texto(R.id.nome, nome)
            linha.texto(R.id.valor, Formato.moeda(valor))
            linha.texto(R.id.percentual, Formato.percentual(valor, total))
        }
        return card
    }

    private fun cardDia(pai: ViewGroup, dia: Dia): View {
        val card = layoutInflater.inflate(R.layout.item_historico_dia, pai, false)
        card.texto(R.id.data, dia.data.format(formatoData).replaceFirstChar { it.uppercase() })

        if (dia.folga) {
            // Dia livre: card neutro e discreto, sem valores.
            card.setBackgroundResource(R.drawable.card_day_livre_bg)
            card.elevation = 0f
            card.alpha = 0.6f
            card.findViewById<View>(R.id.detalhes).visibility = View.GONE
            card.findViewById<View>(R.id.apps).visibility = View.GONE
            card.findViewById<TextView>(R.id.liquido).apply {
                text = "Dia livre"
                setTextColor(getColor(R.color.texto_secundario))
                textSize = 14f
            }
        } else {
            card.texto(R.id.liquido, Formato.moeda(dia.liquido))
            card.texto(R.id.appUrbano, Formato.moeda(dia.valor1))
            card.texto(R.id.app99, Formato.moeda(dia.valor2))
            card.texto(R.id.appFora, Formato.moeda(dia.valor3))
            card.texto(R.id.bruto, Formato.moeda(dia.bruto))
            // Bruto dividido pelos km rodados; sem km lançado não dá para calcular.
            card.texto(R.id.ganhoPorKm, if (dia.kmTotal > 0) "${Formato.moeda(dia.bruto / dia.kmTotal)}/km" else "—")
            card.texto(R.id.rotuloCombustivel, "Combustível · ${"%.0f".format(dia.kmTotal)} km")
            card.texto(R.id.combustivel, "${Formato.moeda(dia.gasto)} (${Formato.percentual(dia.gasto, dia.bruto)})")
        }

        card.setOnClickListener { abrirBalaoNoDia(dia.data.toString()) }
        card.setOnLongClickListener {
            confirmarApagar(dia)
            true
        }
        return card
    }

    private fun tituloMes(mes: YearMonth, diasDoMes: List<Dia>): View {
        val bruto = diasDoMes.sumOf { it.bruto }
        val liquido = bruto - diasDoMes.sumOf { it.gasto }
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            text = "${mes.format(formatoMes).replaceFirstChar { it.uppercase() }}  ·  ${Formato.moeda(liquido)} líquido"
            setTextColor(getColor(R.color.texto_secundario))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(4, (20 * dp).toInt(), 0, (10 * dp).toInt())
        }
    }

    private fun textoVazio() = TextView(this).apply {
        text = "Nenhum dia salvo ainda. Os valores digitados no balão são salvos automaticamente."
        setTextColor(getColor(R.color.texto_secundario))
        textSize = 14f
        setPadding(0, 24, 0, 24)
    }

    private fun View.texto(id: Int, valor: String) {
        findViewById<TextView>(id).text = valor
    }

    private fun plural(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

    private fun confirmarApagar(dia: Dia) {
        AlertDialog.Builder(this)
            .setMessage("Apagar o registro de ${dia.data.format(formatoData)}?")
            .setPositiveButton("Apagar") { _, _ ->
                banco.apagarDia(dia.data)
                mostrarHistorico()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirPermissaoSobreposicao() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    companion object {
        const val EXTRA_HISTORICO = "historico"
        const val EXTRA_EDITAR_DATA = "editarData"
        private const val ESTADO_ABA = "aba"
    }
}
