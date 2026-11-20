package uy.gastos

import android.app.Application
import uy.gastos.sync.TrabajoRevision

class GastosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TrabajoRevision.programar(this)
    }
}
