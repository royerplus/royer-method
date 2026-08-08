package com.royermethod.app.procesado

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.royermethod.app.MainActivity
import com.royermethod.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio que hace el trabajo.
 *
 * Android mata sin avisar lo que corre en una app que no se ve. Un servicio en
 * primer plano es la unica forma de que el sistema respete un proceso largo, y
 * a cambio exige ensenar una notificacion mientras dura: es el trato, y es
 * razonable, porque el usuario tiene derecho a saber que algo suyo esta
 * gastando bateria.
 *
 * Interpolar un clip corto puede pasar de veinte minutos. Sin esto, mirar un
 * mensaje a mitad tiraba todo el trabajo.
 */
class ProcesadoService : Service() {

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tarea: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Procesador.corriendo) return START_NOT_STICKY

        val enLote = intent?.getBooleanExtra(LOTE, false) == true
        if (!enLote && Procesador.trabajo == null) return START_NOT_STICKY
        if (enLote && Procesador.cola.isEmpty()) return START_NOT_STICKY

        arrancarEnPrimerPlano(0, "Preparando")
        Procesador.corriendo = true

        tarea = alcance.launch {
            if (enLote) {
                // Uno detras de otro. Cada resultado se guarda solo: la cola
                // esta pensada para dejarla trabajando sin nadie delante.
                Procesador.hechosDeLote = 0
                val pendientes = Procesador.cola.toList()
                for (t in pendientes) {
                    if (Procesador.cancelado) break
                    // La pantalla mira a Procesador.trabajo para saber que
                    // tareas ensenar, asi que hay que ponerle el actual.
                    Procesador.encargar(t)
                    val r = runCatching {
                        ejecutar(this@ProcesadoService, t) { a ->
                            Procesador.avance = a
                            if (Procesador.cancelado) throw InterruptedException("cancelado")
                            anunciar(a, Procesador.hechosDeLote + 1, pendientes.size)
                        }
                    }
                    r.getOrNull()?.let {
                        guardarEnGaleria(this@ProcesadoService, it.archivo, t.medio.esVideo)
                        Procesador.salida = it
                    }
                    Procesador.hechosDeLote++
                }
                Procesador.corriendo = false
                Procesador.vaciarCola()
            } else {
                val resultado = runCatching {
                    ejecutar(this@ProcesadoService, Procesador.trabajo!!) { a ->
                        Procesador.avance = a
                        if (Procesador.cancelado) throw InterruptedException("cancelado")
                        anunciar(a)
                    }
                }
                Procesador.corriendo = false
                resultado.fold(
                    onSuccess = { Procesador.salida = it },
                    onFailure = {
                        if (!Procesador.cancelado) {
                            Procesador.fallo = it.message ?: "No se pudo procesar el archivo"
                        }
                    }
                )
            }
            detener()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        tarea?.cancel()
        alcance.cancel()
        Procesador.corriendo = false
        super.onDestroy()
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

    /** Solo cuando cambia el porcentaje entero: una notificacion por
     *  fotograma seria cientos de avisos por segundo. */
    private var ultimoPct = -1

    private fun anunciar(a: Avance, deLote: Int = 0, totalLote: Int = 0) {
        val pct = (a.fraccion * 100).toInt()
        if (pct == ultimoPct) return
        ultimoPct = pct
        val base = if (a.total > 0) "Fotograma ${a.hechos} de ${a.total}" else "Procesando"
        val texto = if (totalLote > 0) "Archivo $deLote de $totalLote  ·  $base" else base
        gestor().notify(AVISO, construir(pct, texto))
    }

    private fun arrancarEnPrimerPlano(pct: Int, texto: String) {
        canal()
        val aviso = construir(pct, texto)
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
        val c = NotificationChannel(CANAL, "Procesado", NotificationManager.IMPORTANCE_LOW)
        c.setShowBadge(false)
        gestor().createNotificationChannel(c)
    }

    companion object {
        private const val CANAL = "procesado"
        private const val AVISO = 1
        private const val LOTE = "lote"

        fun arrancar(context: Context, enLote: Boolean = false) {
            val i = Intent(context, ProcesadoService::class.java).putExtra(LOTE, enLote)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, ProcesadoService::class.java))
        }
    }
}
