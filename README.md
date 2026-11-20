# Gastos compartidos (Android)

App Android que lee los avisos de consumo de Itaú desde una casilla de Gmail, guarda cada compra (tarjeta, comercio y monto) y te deja repartir los gastos entre lo personal y una o varias cuentas compartidas, con gráficos por período.

## Instalar en el celular

1. En el celular, abrí la sección **Releases** de este repositorio (con tu usuario de GitHub iniciado) y descargá `gastos.apk` de la última versión.
2. Abrilo. Android te va a pedir permiso para instalar apps desde el navegador: aceptalo.
3. Cada vez que se sube código nuevo, GitHub genera una versión nueva. Se instala encima de la anterior y no perdés datos.

## Configurar

En la pestaña **Ajustes**:

- **Usuario:** la cuenta de Gmail que recibe los avisos (por defecto `cuentasmao689@gmail.com`).
- **Contraseña de aplicación:** se crea en https://myaccount.google.com/apppasswords (necesita la verificación en dos pasos activa).
- **El remitente contiene:** `itau.com.uy`. **El asunto contiene:** `consumo aprobado`.
- **Revisar automáticamente:** con la app abierta revisa con el intervalo que elijas (60 segundos por defecto). Con la app cerrada, Android permite revisar cada 15 minutos.

## Pantallas

- **Resumen:** gráfico de los gastos del período por cuenta (Personal y cada cuenta compartida) y evolución de los últimos 6 períodos. Por defecto muestra el mes actual; se puede cambiar a semana, trimestre o año y moverse con las flechas.
- **Movimientos:** lista del período. Tocá uno para cambiar a qué cuenta se carga esa compra puntual. El botón + agrega ingresos o gastos a mano.
- **Comercios:** nombre propio para cada comercio y la cuenta a la que se cargan siempre sus compras.
- **Cuentas:** cuentas compartidas ("Casa", "Viaje", etc.).
- **Ajustes:** conexión al correo, estado de la última revisión y correos que no se pudieron leer.

## Desarrollo

- `parser/`: lectura del texto de los avisos de Itaú (Kotlin puro, con pruebas: `./gradlew :parser:test`).
- `app/`: app Android con Jetpack Compose, SQLite, WorkManager y JavaMail para IMAP.
- `.github/workflows/android.yml`: compila el APK en cada push a `main` y lo publica en Releases.

La llave de firma (`app/gastos.keystore`) está en el repositorio para que cada versión se pueda instalar encima de la anterior. Mantené el repositorio privado.
