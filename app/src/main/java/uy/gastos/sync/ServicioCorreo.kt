package uy.gastos.sync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uy.gastos.data.Ajustes

/**
 * Revisa el correo todo el tiempo, con la app abierta o cerrada. Android exige mostrar un aviso
 * fijo mientras corre; va en un canal silencioso.
 */
class ServicioCorreo : Service() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ciclo: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cfg = Ajustes(this).leer()
        Notificaciones.crearCanales(this)
        val aviso = Notificaciones.base(this, Notificaciones.CANAL_SERVICIO)
            .setContentTitle("Revisando el correo")
            .setContentText("Busca consumos nuevos cada ${cfg.intervaloSegundos} segundos")
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Notificaciones.abrir(this, 1) { putExtra(Notificaciones.EXTRA_PANTALLA, "ajustes") })
            .build()
        try {
            ServiceCompat.startForeground(this, Notificaciones.ID_SERVICIO, aviso,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (e: Exception) {
            Log.w("ServicioCorreo", "No se pudo iniciar en primer plano", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!cfg.activado) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ciclo?.isActive != true) {
            ciclo = alcance.launch {
                while (isActive) {
                    val actual = Ajustes(this@ServicioCorreo).leer()
                    if (!actual.activado) break
                    LectorCorreo.revisar(this@ServicioCorreo)
                    delay(actual.intervaloSegundos.coerceAtLeast(30) * 1000L)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }

    companion object {
        /** Arranca o detiene el servicio según los ajustes. Devuelve false si Android no lo dejó arrancar. */
        fun sincronizar(ctx: Context): Boolean {
            val intent = Intent(ctx, ServicioCorreo::class.java)
            if (!Ajustes(ctx).leer().activado) {
                ctx.stopService(intent)
                return true
            }
            return try {
                ContextCompat.startForegroundService(ctx, intent)
                true
            } catch (e: Exception) {
                // Desde segundo plano Android 12+ puede no permitirlo; queda la revisión cada 15 minutos.
                Log.w("ServicioCorreo", "No se pudo arrancar", e)
                false
            }
        }
    }
}
