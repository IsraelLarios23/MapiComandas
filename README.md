# MapiComandas — Módulo Restaurante Android

App Android nativa (Kotlin + Jetpack Compose) que replica el módulo de restaurante de MapiPOS.
Comparte la misma base de datos SQL Server / Azure SQL que el POS de escritorio.

## Arquitectura

```
MVVM + Repository + Hilt DI + Coroutines + StateFlow
```

```
app/
└── java/com/example/mapicomandas/
    ├── MapiComandasApp.kt          ← @HiltAndroidApp
    ├── MainActivity.kt             ← @AndroidEntryPoint
    ├── SessionManager.kt           ← EncryptedSharedPreferences (IdTienda, IdCaja, etc.)
    ├── data/
    │   ├── model/Models.kt         ← Data classes + enums de status
    │   ├── remote/ApiService.kt    ← Retrofit endpoints
    │   └── repository/             ← Interface + Impl
    ├── di/
    │   ├── NetworkModule.kt        ← Retrofit / OkHttp
    │   └── RepositoryModule.kt     ← Hilt bindings
    ├── util/
    │   └── ImpuestosCalculator.kt  ← Motor de IVA/IEPS exacto
    └── ui/
        ├── navigation/NavGraph.kt  ← Compose Navigation
        └── screens/
            ├── mesas/              ← Plano de mesas + VM
            ├── comanda/            ← Captura POS + VM
            ├── kds/                ← Monitor de cocina + VM
            ├── cobro/              ← Cobro / pagos + VM
            ├── domicilio/          ← Domicilio / para llevar + VM
            └── caja/               ← Caja / corte + VM
```

## Configuración

### 1. Backend REST (Opción A — recomendada)

Despliega un backend ASP.NET Core o Node.js que ejecute las queries T-SQL del spec.
El backend se conecta a SQL Server; el APK consume HTTP.

Configura la URL del backend en `SessionManager` (se guarda en `EncryptedSharedPreferences`):

```kotlin
sessionManager.guardarConfiguracion(
    baseUrl  = "https://tu-servidor.com",   // o http://192.168.1.x:5000 en LAN
    idTienda = 1,
    idCaja   = 1,
    idAlmacen = 1
)
```

En la primera versión cambia el valor por defecto en `SessionManager.kt`:

```kotlin
baseUrl = prefs.getString("baseUrl", "http://10.0.2.2:5000")
//  ↑ 10.0.2.2 = localhost desde el emulador Android
```

### 2. Tablas SQL Server requeridas

El backend debe conectar a una BD con exactamente estas tablas (ver spec §2):

| Tabla | Propósito |
|-------|-----------|
| `Mesas` | Plano de mesas + layout |
| `MaestroComandas` | Cabeceras de comandas |
| `DetalleComandas` | Líneas de artículos |
| `DetalleComandaModificadores` | Modificadores aplicados |
| `DetalleComandaKitItems` | Componentes de kit elegidos |
| `Articulos`, `Categorias` | Catálogo |
| `Meseros` | Personal |
| `PuntosImpresion`, `PuntosImpresionCategorias` | Ruteo de impresión |
| `RepartidoresRest`, `ZonasReparto` | Domicilio |
| `Ventas`, `DetalleVentas`, `Pagos`, `PagosVenta` | Cobro / facturación |
| `ConfiguracionSistema` | Claves `REST_*` |

### 3. Claves de configuración (`ConfiguracionSistema`)

| Clave | Valor por defecto | Descripción |
|-------|-------------------|-------------|
| `REST_FILTRAR_PLATILLOS` | `false` | Filtrar artículos por EsPlatillo |
| `REST_MOSTRAR_PRECIO_BOTON` | `true` | Mostrar precio en botón |
| `REST_PEDIR_PERSONAS` | `true` | Pedir # de comensales al abrir |
| `REST_PEDIR_NOTAS` | `true` | Pedir notas al agregar artículo |
| `REST_PEDIR_CANTIDAD` | `false` | Agregar siempre cantidad 1 si false |
| `REST_TASA_IVA` | `0.16` | Tasa de IVA por defecto |
| `REST_PROPINA_MODO` | `GLOBAL` | GLOBAL o POR_GRUPO |
| `REST_PROPINA_GLOBAL` | `0.10` | % de propina sugerida global |
| `REST_COMIDA_RAPIDA` | `false` | Tras cobrar, nueva venta sin cerrar |
| `REST_IMPRIMIR_PUNTOS_AL_COBRAR` | `false` | Imprime en puntos al cerrar |
| `REST_LEYENDA_CUENTA` | `` | Texto al pie de la cuenta |
| `ART_MINIATURA_SEGUNDOS` | `5` | Intervalo de logos alternos |
| `POS_LOGO_PERSONALIZADO_B64` | `` | Logo cliente en base64 |

### 4. Impresoras ESC/POS

Configura cada punto de impresión en la tabla `PuntosImpresion`:

```sql
INSERT INTO PuntosImpresion (Nombre, Impresora, Ancho, Copias, ImprimirAlEnviar, Activo)
VALUES ('Cocina', '192.168.1.200', 42, 1, 1, 1),
       ('Barra',  '192.168.1.201', 42, 1, 1, 1);
```

El campo `Impresora` acepta:
- IP de red: `192.168.1.200`
- Puerto COM: `COM3`
- Nombre Bluetooth

### 5. Concurrencia multi-caja

Las queries críticas usan `WITH (UPDLOCK, HOLDLOCK)`:

- **Abrir comanda**: candado en la mesa para evitar doble apertura.
- **Agregar línea**: `MAX(Linea)+1` con candado para folios únicos por comanda.
- **Cerrar comanda**: transacción atómica crea Venta + libera mesa.

Nunca reusar folios serie "K" (comandas) ni "T" (tickets de venta).

### 6. Build

```bash
# Clonar y abrir en Android Studio Meerkat o superior
./gradlew assembleDebug
```

Requiere:
- JDK 11+
- Android Studio Meerkat (AGP 8.8)
- `minSdk 26` (Android 8.0+)

## Pantallas implementadas

| Pantalla | Ruta | Descripción |
|----------|------|-------------|
| Plano de mesas | `/mesas` | Layout del editor, status, cronómetro, importe |
| Captura comanda | `/comanda/{id}` | Categorías + artículos + grid líneas |
| KDS Cocina | `/kds` | Tarjetas por comanda, demora cromática |
| Cobro | `/cobro/{id}` | Pagos múltiples, propina, cambio |
| Domicilio | `/domicilio` | Pedidos sin mesa, repartidores, zonas |
| Caja | `/caja` | Resumen, movimientos, corte X/Z |

## Modelo de datos → Enums

```kotlin
StatusMesa:    1=Libre  2=Ocupada  3=Reservada  4=CuentaPedida  5=EnLimpieza
StatusComanda: 1=Abierta 2=EnCocina 3=Lista 4=CuentaPedida 5=Cerrada 6=Cancelada
StatusLinea:   1=Pendiente 2=EnCocina 3=Listo 4=Entregado 5=Cancelado
TipoServicio:  1=Comedor 2=ParaLlevar 3=Domicilio
StatusEntrega: 0=N/A 1=Pendiente 2=EnCamino 3=Entregado
TipoModificador: 1=Quita 2=AgregaGratis 3=AgregaConCosto
```
