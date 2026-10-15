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
            // Alarme sem horário exato (não exige permissão extra); dispara em até 10 min.
            alarmes.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60 * 1000L, intentDoAlarme(context, acao))
        }
    }

    private fun intentDoAlarme(context: Context, acao: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            acao.hashCode(),
            Intent(context, LembreteReceiver::class.java).setAction(acao),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun verificar(context: Context, acao: String) {
        val data = when (acao) {
            ACAO_NOITE -> LocalDate.now()
            ACAO_MANHA -> LocalDate.now().minusDays(1)
            else -> return
        }
        val registrado = BancoDados(context).use { it.buscar(data)?.registrado == true }
        if (!registrado) notificar(context, data, acao == ACAO_NOITE)
    }

    private fun notificar(context: Context, data: LocalDate, hoje: Boolean) {
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

        val quando = if (hoje) "hoje" else "ontem"
        val notificacao = Notification.Builder(context, CANAL)
            .setContentTitle("Você não registrou $quando")
            .setContentText("Toque para lançar os ganhos de $quando ou marcar dia livre.")
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        gerenciador.notify(if (hoje) 10 else 11, notificacao)
    }
}

class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Também chega após reiniciar o celular, quando os alarmes precisam ser refeitos.
        Lembretes.verificar(context, intent.action ?: "")
        Lembretes.agendar(context)
    }
}
