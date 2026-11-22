package uy.gastos.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Vuelve a arrancar la revisión al prender el celular o al actualizar la app. */
class Arranque : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON" -> {
                TrabajoRevision.programar(ctx)
                ServicioCorreo.sincronizar(ctx)
            }
        }
    }
}
