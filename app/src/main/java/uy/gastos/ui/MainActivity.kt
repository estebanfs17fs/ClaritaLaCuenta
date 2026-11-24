package uy.gastos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
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
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import uy.gastos.sync.Notificaciones
import uy.gastos.sync.ServicioCorreo

private enum class Pestana(val titulo: String, val icono: ImageVector) {
    RESUMEN("Resumen", Icons.Filled.PieChart),
    MOVIMIENTOS("Actividad", Icons.Filled.ReceiptLong),
    COMERCIOS("Comercios", Icons.Filled.Storefront),
    CUENTAS("Cuentas", Icons.Filled.Group),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

/** Pedidos de navegación que llegan desde las notificaciones. */
object Navegacion {
    val pantalla = MutableStateFlow<String?>(null)
    val movimiento = MutableStateFlow<Long?>(null)

    fun desde(intent: Intent?) {
        intent ?: return
        val id = intent.getLongExtra(Notificaciones.EXTRA_MOVIMIENTO, -1L)
        if (id > 0) { movimiento.value = id; pantalla.value = "actividad" }
        intent.getStringExtra(Notificaciones.EXTRA_PANTALLA)?.let { pantalla.value = it }
        intent.removeExtra(Notificaciones.EXTRA_MOVIMIENTO)
        intent.removeExtra(Notificaciones.EXTRA_PANTALLA)
    }
}

class MainActivity : ComponentActivity() {
    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        ServicioCorreo.sincronizar(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) Navegacion.desde(intent)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { Tema { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Navegacion.desde(intent)
    }

    override fun onResume() {
        super.onResume()
        // Con la app en primer plano Android siempre deja arrancar el servicio.
        ServicioCorreo.sincronizar(this)
    }
}

@Composable
private fun App() {
    var pestana by rememberSaveable { mutableStateOf(Pestana.RESUMEN) }
    var periodo by rememberSaveable { mutableStateOf(Periodo.MES) }
    var ancla by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val alCambiarPeriodo: (Periodo, LocalDate) -> Unit = { p, f -> periodo = p; ancla = f.toString() }
    // Filtro de cuentas compartido entre Resumen y Movimientos ("" = todas; 0 = Personal).
    var filtroTexto by rememberSaveable { mutableStateOf("") }
    val filtro = filtroTexto.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    val alCambiarFiltro: (Set<Long>) -> Unit = { filtroTexto = it.joinToString(",") }

    // Al tocar una notificación, ir a la pantalla que corresponde.
    val pedido by Navegacion.pantalla.collectAsState()
    LaunchedEffect(pedido) {
        when (pedido) {
            "actividad" -> pestana = Pestana.MOVIMIENTOS
            "ajustes" -> pestana = Pestana.AJUSTES
        }
        if (pedido != null) Navegacion.pantalla.value = null
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
        val reducido = movimientoReducido()
        // Al cambiar de pestaña, la pantalla nueva entra desde el lado de la pestaña tocada.
        AnimatedContent(
            targetState = pestana,
            transitionSpec = {
                ejeHorizontal(if (targetState.ordinal > initialState.ordinal) 1 else -1, reducido)
            },
            label = "pestana",
        ) { p ->
            when (p) {
                Pestana.RESUMEN -> PantallaResumen(m, periodo, fecha, alCambiarPeriodo, filtro, alCambiarFiltro)
                Pestana.MOVIMIENTOS -> PantallaMovimientos(m, periodo, fecha, alCambiarPeriodo, filtro, alCambiarFiltro)
                Pestana.COMERCIOS -> PantallaComercios(m)
                Pestana.CUENTAS -> PantallaCuentas(m)
                Pestana.AJUSTES -> PantallaAjustes(m)
            }
        }
    }
}
