package uy.gastos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import kotlinx.coroutines.delay
import uy.gastos.data.Ajustes
import uy.gastos.sync.LectorCorreo

private enum class Pestana(val titulo: String, val icono: ImageVector) {
    RESUMEN("Resumen", Icons.Filled.PieChart),
    MOVIMIENTOS("Actividad", Icons.Filled.ReceiptLong),
    COMERCIOS("Comercios", Icons.Filled.Storefront),
    CUENTAS("Cuentas", Icons.Filled.Group),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Tema { App() } }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    var pestana by rememberSaveable { mutableStateOf(Pestana.RESUMEN) }
    var periodo by rememberSaveable { mutableStateOf(Periodo.MES) }
    var ancla by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val alCambiarPeriodo: (Periodo, LocalDate) -> Unit = { p, f -> periodo = p; ancla = f.toString() }
    // Filtro de cuentas compartido entre Resumen y Movimientos ("" = todas; 0 = Personal).
    var filtroTexto by rememberSaveable { mutableStateOf("") }
    val filtro = filtroTexto.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    val alCambiarFiltro: (Set<Long>) -> Unit = { filtroTexto = it.joinToString(",") }

    // Mientras la app está abierta, revisa el correo con el intervalo configurado.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        ciclo.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val cfg = Ajustes(ctx).leer()
                if (cfg.activado) LectorCorreo.revisar(ctx)
                delay(cfg.intervaloSegundos.coerceAtLeast(30) * 1000L)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == pestana, onClick = { pestana = p },
                        icon = { Icon(p.icono, contentDescription = null) },
                        label = { Text(p.titulo, maxLines = 1) },
                    )
                }
            }
        },
    ) { relleno ->
        val m = Modifier.padding(relleno)
        val fecha = LocalDate.parse(ancla)
        when (pestana) {
            Pestana.RESUMEN -> PantallaResumen(m, periodo, fecha, alCambiarPeriodo, filtro, alCambiarFiltro)
            Pestana.MOVIMIENTOS -> PantallaMovimientos(m, periodo, fecha, alCambiarPeriodo, filtro, alCambiarFiltro)
            Pestana.COMERCIOS -> PantallaComercios(m)
            Pestana.CUENTAS -> PantallaCuentas(m)
            Pestana.AJUSTES -> PantallaAjustes(m)
        }
    }
}
