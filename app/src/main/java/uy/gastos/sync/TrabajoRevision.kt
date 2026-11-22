package uy.gastos.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import uy.gastos.data.Ajustes

/** Revisión en segundo plano: Android permite como mínimo cada 15 minutos. */
class TrabajoRevision(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (Ajustes(applicationContext).leer().activado) {
            // Respaldo por si Android cerró el servicio continuo: intenta reabrirlo y revisa igual.
            ServicioCorreo.sincronizar(applicationContext)
            LectorCorreo.revisar(applicationContext)
        }
        return Result.success()
    }

    companion object {
        fun programar(ctx: Context) {
            val pedido = PeriodicWorkRequestBuilder<TrabajoRevision>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "revisar-correo", ExistingPeriodicWorkPolicy.KEEP, pedido,
            )
        }
    }
}
