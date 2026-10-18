package com.calculadoraflutuante

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// Cobra o registro do dia: às 23h se hoje está vazio, e às 8h se ontem ficou vazio.
object Lembretes {
    private const val ACAO_NOITE = "com.calculadoraflutuante.LEMBRETE_NOITE"
    private const val ACAO_MANHA = "com.calculadoraflutuante.LEMBRETE_MANHA"
    private const val CANAL = "lembretes"
    private const val EXTRA_DIA = "dia"

    private val horarios = mapOf(
        ACAO_NOITE to LocalTime.of(23, 0),
        ACAO_MANHA to LocalTime.of(8, 0)
    )

    fun agendar(context: Context) {
        val alarmes = context.getSystemService(AlarmManager::class.java)
        horarios.forEach { (acao, hora) ->
            var quando = LocalDateTime.of(LocalDate.now(), hora)
            if (!quando.isAfter(LocalDateTime.now())) quando = quando.plusDays(1)
            val millis = quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // O alarme leva o dia que ele confere: o das 23h confere o próprio dia, o das 8h o
            // anterior. Assim, mesmo que o Android atrase o alarme, o dia conferido é o certo.
            val dia = if (acao == ACAO_NOITE) quando.toLocalDate() else quando.toLocalDate().minusDays(1)
            // Sem horário exato (não exige permissão extra), mas dispara mesmo com o celular
            // em economia de bateria, em vez de esperar o aparelho "acordar".
            alarmes.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intentDoAlarme(context, acao, dia))
        }
    }

    private fun intentDoAlarme(context: Context, acao: String, dia: LocalDate): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            acao.hashCode(),
            Intent(context, LembreteReceiver::class.java).setAction(acao).putExtra(EXTRA_DIA, dia.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun verificar(context: Context, intent: Intent) {
        val data = intent.getStringExtra(EXTRA_DIA)?.let { LocalDate.parse(it) } ?: when (intent.action) {
            ACAO_NOITE -> LocalDate.now()
            ACAO_MANHA -> LocalDate.now().minusDays(1)
            else -> return
        }
        if (intent.action != ACAO_NOITE && intent.action != ACAO_MANHA) return
        val registrado = BancoDados(context).use { it.buscar(data)?.registrado == true }
        if (!registrado) notificar(context, data)
    }

    private val formatoDia = java.time.format.DateTimeFormatter.ofPattern("EEE, dd/MM", java.util.Locale.forLanguageTag("pt-BR"))

    private fun notificar(context: Context, data: LocalDate) {
        val gerenciador = context.getSystemService(NotificationManager::class.java)
        gerenciador.createNotificationChannel(
            NotificationChannel(CANAL, "Lembretes de registro", NotificationManager.IMPORTANCE_DEFAULT)
        )

        // Tocar na notificação abre o balão já no dia que ficou sem registro.
        val abrir = PendingIntent.getActivity(
            context,
            data.hashCode(),
            Intent(context, HistoricoActivity::class.java)
                .putExtra(HistoricoActivity.EXTRA_EDITAR_DATA, data.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // O texto diz de que dia se trata comparando com a hora em que a notificação aparece.
        val hoje = LocalDate.now()
        val (titulo, dia) = when (data) {
            hoje -> "hoje" to "hoje"
            hoje.minusDays(1) -> "ontem (${data.format(formatoDia)})" to "ontem"
            else -> "em ${data.format(formatoDia)}" to data.format(formatoDia)
        }
        val notificacao = Notification.Builder(context, CANAL)
            .setContentTitle("Você não registrou $titulo")
            .setContentText("Toque para lançar os ganhos de $dia ou marcar dia livre.")
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        // Uma notificação por dia: o lembrete da manhã substitui o da noite do mesmo dia.
        gerenciador.notify(1000 + (data.toEpochDay() % 100000).toInt(), notificacao)
    }
}

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Também chega após reiniciar o celular, quando os alarmes precisam ser refeitos.
        Lembretes.verificar(context, intent)
        Lembretes.agendar(context)
    }
}
