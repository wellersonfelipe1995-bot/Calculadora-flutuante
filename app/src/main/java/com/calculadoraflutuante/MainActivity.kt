package com.calculadoraflutuante

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

// Entrada do ícone do app. Não desenha nada: com a permissão já dada só liga o balão;
// sem ela abre a tela principal para o usuário permitir.
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            Lembretes.agendar(this)
            startForegroundService(Intent(this, FloatingService::class.java))
        } else {
            startActivity(Intent(this, HistoricoActivity::class.java))
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
