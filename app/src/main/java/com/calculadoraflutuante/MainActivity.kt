package com.calculadoraflutuante

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import java.time.LocalDate

// Entrada do ícone do app. Não desenha nada: com a permissão já dada liga o balão
// já com o painel aberto no dia de hoje; sem ela abre a tela principal para permitir.
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            Lembretes.agendar(this)
            startForegroundService(
                Intent(this, FloatingService::class.java)
                    .putExtra(FloatingService.EXTRA_DATA, LocalDate.now().toString())
            )
        } else {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
