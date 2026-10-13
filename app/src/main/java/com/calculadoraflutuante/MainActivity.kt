package com.calculadoraflutuante

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

    private fun abrirPermissaoSobreposicao() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }
}
