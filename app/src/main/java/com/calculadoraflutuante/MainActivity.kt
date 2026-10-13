package com.calculadoraflutuante

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var banco: BancoDados
    private val formatoData = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        banco = BancoDados(this)

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

    private fun mostrarHistorico() {
        val lista = findViewById<LinearLayout>(R.id.historico)
        lista.removeAllViews()

        val dias = banco.resumoPorDia().filter { it.bruto > 0 || it.gasto > 0 }
        if (dias.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "Nenhuma corrida salva ainda. Use o botão Salvar no balão."
                setPadding(0, 16, 0, 0)
            })
            return
        }

        val dp = resources.displayMetrics.density
        dias.forEach { dia ->
            lista.addView(TextView(this).apply {
                text = buildString {
                    append(dia.data.format(formatoData).replaceFirstChar { it.uppercase() })
                    append("\n${dia.corridas} corrida(s) · ${Formato.moeda(dia.bruto)} bruto")
                    append("\nCombustível: ${Formato.moeda(dia.gasto)}")
                    append(" (${Formato.percentual(dia.gasto, dia.bruto)}) · ${"%.0f".format(dia.km)} km")
                    append("\nLíquido: ${Formato.moeda(dia.liquido)}")
                }
                textSize = 15f
                setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
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

    private fun confirmarApagar(dia: ResumoDia) {
        AlertDialog.Builder(this)
            .setMessage("Apagar todas as corridas de ${dia.data.format(formatoData)}?")
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
}
