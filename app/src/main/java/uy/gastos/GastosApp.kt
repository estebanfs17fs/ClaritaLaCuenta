package uy.gastos

import android.app.Application
import uy.gastos.sync.Notificaciones
import uy.gastos.sync.TrabajoRevision

class GastosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notificaciones.crearCanales(this)
        TrabajoRevision.programar(this)
    }
}
