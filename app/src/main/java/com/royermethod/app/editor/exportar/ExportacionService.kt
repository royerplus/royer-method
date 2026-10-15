package com.royermethod.app.editor.exportar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.royermethod.app.MainActivity
import com.royermethod.app.R
import com.royermethod.app.procesado.guardarEnGaleria
import java.io.File

/**
 * Hace la exportacion en primer plano, con su notificacion.
 *
 * El trabajo corre en un hilo propio que es el que tiene el contexto de
 * OpenGL: no puede saltar de hilo a mitad, que es lo que haria una
 * corrutina. Cancelar no para el servicio a lo bruto: avisa, el hilo termina
 * el fotograma que tenga entre manos, suelta los codificadores y es el
 * quien cierra el servicio. Asi nunca quedan dos exportaciones a la vez.
 */
class ExportacionService : Service() {

    private var hilo: Thread? = null
    private val principal = Handler(Looper.getMainLooper())
    private var ultimoPct = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (hilo?.isAlive == true) return START_NOT_STICKY
        val proyecto = Exportacion.proyecto
        if (proyecto == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        enPrimerPlano(construir(0, "Preparando"))

        hilo = Thread({
            val destino = File(cacheDir, "exportado.mp4")
            val r = runCatching {
                Exportador(this, proyecto, destino, cancelado = { Exportacion.cancelado }) { hechos, total ->
                    principal.post {
                        Exportacion.hechos = hechos
                        Exportacion.total = total
                    }
                    anunciar(hechos, total)
                }.exportar()
            }
            val uri = r.getOrNull()?.let { guardarEnGaleria(this, it.archivo, esVideo = true) }
            principal.post {
                r.fold(
                    onSuccess = {
                        Exportacion.informe = it
                        Exportacion.guardado = uri
                        if (uri == null) Exportacion.fallo = "Se exporto pero no se pudo guardar en la galeria"
                    },
                    onFailure = {
                        if (it !is Cancelado) Exportacion.fallo = it.message ?: "No se pudo exportar"
                    }
                )
                Exportacion.corriendo = false
                detener()
            }
        }, "exportar")
        hilo!!.start()
        return START_NOT_STICKY
    }

    private fun anunciar(hechos: Long, total: Long) {
        val pct = if (total > 0) (hechos * 100 / total).toInt() else 0
        if (pct == ultimoPct) return
        ultimoPct = pct
        gestor().notify(AVISO, construir(pct, "Exportando  ·  $pct%"))
    }

    private fun detener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun enPrimerPlano(aviso: Notification) {
        canal()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(AVISO, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(AVISO, aviso)
        }
    }

    private fun construir(pct: Int, texto: String): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Royer Method")
            .setContentText(texto)
            .setSmallIcon(R.drawable.ic_logo_mono)
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(abrir)
            .build()
    }

    private fun gestor(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun canal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val c = NotificationChannel(CANAL, "Exportar", NotificationManager.IMPORTANCE_LOW)
        c.setShowBadge(false)
        gestor().createNotificationChannel(c)
    }

    companion object {
        private const val CANAL = "exportar"
        private const val AVISO = 2

        internal fun arrancar(context: Context) {
            val i = Intent(context, ExportacionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
