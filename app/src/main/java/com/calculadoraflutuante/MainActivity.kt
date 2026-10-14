package com.calculadoraflutuante

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var banco: BancoDados
    private val formatoData = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    private val formatoMes = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        banco = BancoDados(this)
        Lembretes.agendar(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

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
    }

    override fun onResume() {
        super.onResume()
        mostrarHistorico()
    }

    override fun onDestroy() {
        super.onDestroy()
        banco.close()
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

    private fun mostrarHistorico() {
        val lista = findViewById<LinearLayout>(R.id.historico)
        lista.removeAllViews()

        val dias = banco.todosOsDias().filter { it.registrado }
        if (dias.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "Nenhum dia salvo ainda. Os valores digitados no balão são salvos automaticamente."
                setPadding(0, 16, 0, 0)
            })
        }

        val dp = resources.displayMetrics.density
        dias.groupBy { YearMonth.from(it.data) }.forEach { (mes, diasDoMes) ->
            // Resumo do mês, com destaque para as corridas por fora (3ª caixa).
            val porFora = diasDoMes.sumOf { it.valor3 }
            val bruto = diasDoMes.sumOf { it.bruto }
            val gasto = diasDoMes.sumOf { it.gasto }
            val trabalhados = diasDoMes.count { !it.folga && it.bruto > 0 }
            val livres = diasDoMes.count { it.folga }
            lista.addView(TextView(this).apply {
                text = buildString {
                    append(mes.format(formatoMes).replaceFirstChar { it.uppercase() })
                    append("\nPor fora no mês: ${Formato.moeda(porFora)}")
                    append("\nBruto: ${Formato.moeda(bruto)} · Combustível: ${Formato.moeda(gasto)}")
                    append("\nLíquido: ${Formato.moeda(bruto - gasto)}")
                    append("\n$trabalhados dia(s) trabalhado(s) · $livres dia(s) livre(s)")
                    if (trabalhados > 0) {
                        append("\nMédia por dia trabalhado: ${Formato.moeda(bruto / trabalhados)} bruto")
                        append(" · ${Formato.moeda((bruto - gasto) / trabalhados)} líquido")
                    }
                }
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundColor(0x1A2E7D32)
                val p = (12 * dp).toInt()
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (16 * dp).toInt() }
            })

            diasDoMes.forEach { dia ->
                lista.addView(TextView(this).apply {
                    text = if (dia.folga) {
                        dia.data.format(formatoData).replaceFirstChar { it.uppercase() } + "\nDia livre"
                    } else buildString {
                        append(dia.data.format(formatoData).replaceFirstChar { it.uppercase() })
                        append("\nBruto: ${Formato.moeda(dia.bruto)} · Por fora: ${Formato.moeda(dia.valor3)}")
                        append("\nCombustível: ${Formato.moeda(dia.gasto)}")
                        append(" (${Formato.percentual(dia.gasto, dia.bruto)}) · ${"%.0f".format(dia.km)} km")
                        append("\nLíquido: ${Formato.moeda(dia.liquido)}")
                    }
                    textSize = 15f
                    setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
                    setOnClickListener { abrirBalaoNoDia(dia.data.toString()) }
                    setOnLongClickListener {
                        confirmarApagar(dia)
                        true
                    }
                })
                lista.addView(View(this).apply {
                    setBackgroundColor(0x22000000)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
                })
            }
        }

        // Aberto pelo botão Histórico do balão: rola direto para o histórico.
        if (intent.getBooleanExtra(EXTRA_HISTORICO, false)) {
            intent.removeExtra(EXTRA_HISTORICO)
            val rolagem = findViewById<ScrollView>(R.id.rolagem)
            val titulo = findViewById<View>(R.id.tituloHistorico)
            rolagem.post { rolagem.scrollTo(0, titulo.top) }
        }
    }

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
    }
}
